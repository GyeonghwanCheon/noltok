"""
Phase 9-3-2 부하 테스트 — 채팅 메시지 전송(WebSocket/STOMP -> Kafka -> DB -> 브로드캐스트)

k6(v2.1.0)의 k6/websockets 모듈이 동시 연결 상황에서 onopen/onmessage
콜백이 사실상 발화하지 않는 결함이 있어(docs/troubleshooting-log.md 참고,
VU 여러 개든 프로세스 여러 개든 동일하게 재현됨) k6 대신 asyncio +
websockets 라이브러리로 직접 동시 접속 클라이언트를 구현한다.

SockJS(.withSockJS()) 엔드포인트라도 "/ws/websocket" 경로는 SockJS
프레이밍 없이 순수 STOMP 프레임을 그대로 받는 raw WebSocket transport라
SockJS 클라이언트 없이 바로 접속 가능 (docs/decision-log.md 참고).

사전 준비:
  1. docker-compose.prod.yml 환경 + 서버가 떠 있어야 함
  2. python3 scripts/prepare_load_test_data.py 로 load_test_data.json 생성
     (JWT 액세스 토큰 유효시간이 30분이라 오래 전에 만든 파일이면 재생성 필요)

실행: python3 scripts/load_test_chat.py [--concurrency 30] [--duration 120]
"""

import argparse
import asyncio
import json
import random
import statistics
import time

import websockets

WS_URL = "ws://localhost:8080/ws/websocket"
SEND_INTERVAL_MIN = 4
SEND_INTERVAL_MAX = 6
STARTUP_JITTER_MAX = 3


async def run_client(idx, entry, duration, results):
    room_id = entry["roomId"]
    token = entry["token"]
    sent_at = {}
    sent_count = 0
    received_count = 0
    round_trips_ms = []

    await asyncio.sleep(random.uniform(0, STARTUP_JITTER_MAX))

    try:
        async with websockets.connect(WS_URL, open_timeout=10) as ws:
            await ws.send(f"CONNECT\naccept-version:1.2\nAuthorization:Bearer {token}\n\n\x00")
            connected_frame = await asyncio.wait_for(ws.recv(), timeout=10)
            if not connected_frame.startswith("CONNECTED"):
                results.append({"idx": idx, "error": f"CONNECT 실패: {connected_frame[:100]}"})
                return

            await ws.send(f"SUBSCRIBE\nid:sub-0\ndestination:/topic/rooms/{room_id}\n\n\x00")

            async def sender():
                nonlocal sent_count
                end_time = time.monotonic() + duration
                while time.monotonic() < end_time:
                    await asyncio.sleep(random.uniform(SEND_INTERVAL_MIN, SEND_INTERVAL_MAX))
                    marker = f"pytest-{idx}-{time.time_ns()}"
                    sent_at[marker] = time.monotonic()
                    body = json.dumps({"type": "TEXT", "content": marker, "fileUrl": None})
                    await ws.send(
                        f"SEND\ndestination:/app/rooms/{room_id}/messages\n"
                        f"content-type:application/json\n\n{body}\x00"
                    )
                    sent_count += 1

            async def receiver():
                nonlocal received_count
                while True:
                    frame = await ws.recv()
                    if not frame.startswith("MESSAGE"):
                        continue
                    received_count += 1
                    body_start = frame.find("\n\n")
                    if body_start == -1:
                        continue
                    try:
                        payload = json.loads(frame[body_start + 2:].rstrip("\x00"))
                    except json.JSONDecodeError:
                        continue
                    marker = payload.get("content")
                    if marker in sent_at:
                        round_trips_ms.append((time.monotonic() - sent_at.pop(marker)) * 1000)

            receiver_task = asyncio.create_task(receiver())
            await sender()
            await asyncio.sleep(3)  # 마지막 전송의 브로드캐스트 응답 대기
            receiver_task.cancel()
            try:
                await receiver_task
            except asyncio.CancelledError:
                pass
    except Exception as e:
        results.append({"idx": idx, "error": str(e)})
        return

    results.append({
        "idx": idx,
        "sent": sent_count,
        "received": received_count,
        "round_trips_ms": round_trips_ms,
    })


async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--concurrency", type=int, default=30)
    parser.add_argument("--duration", type=int, default=120)
    parser.add_argument("--data", default="scripts/load_test_data.json")
    args = parser.parse_args()

    entries = json.load(open(args.data))
    if args.concurrency > len(entries):
        raise SystemExit(f"concurrency({args.concurrency})가 데이터 건수({len(entries)})보다 많습니다.")

    print(f"동시 연결 {args.concurrency}개, 연결당 {args.duration}초 부하 테스트 시작...")
    results = []
    await asyncio.gather(*[
        run_client(i, entries[i], args.duration, results)
        for i in range(args.concurrency)
    ])

    errors = [r for r in results if "error" in r]
    ok = [r for r in results if "error" not in r]

    total_sent = sum(r["sent"] for r in ok)
    total_received = sum(r["received"] for r in ok)
    all_rtt = [t for r in ok for t in r["round_trips_ms"]]

    print()
    print(f"연결 성공: {len(ok)}/{args.concurrency}, 실패: {len(errors)}")
    for e in errors:
        print(f"  - VU{e['idx']}: {e['error']}")
    print(f"전송 메시지 총합: {total_sent}")
    print(f"수신(왕복 확인) 총합: {total_received}")
    if total_sent:
        print(f"메시지 유실률: {(1 - total_received / total_sent) * 100:.2f}%")
    if all_rtt:
        print(f"왕복 지연시간(ms) - 평균: {statistics.mean(all_rtt):.2f}")
        print(f"왕복 지연시간(ms) - 중앙값: {statistics.median(all_rtt):.2f}")
        print(f"왕복 지연시간(ms) - p95: {statistics.quantiles(all_rtt, n=20)[18]:.2f}")
        print(f"왕복 지연시간(ms) - 최댓값: {max(all_rtt):.2f}")

    with open("/tmp/load_test_chat_result.json", "w") as f:
        json.dump(results, f)
    print("\n원본 결과: /tmp/load_test_chat_result.json")


if __name__ == "__main__":
    asyncio.run(main())
