"""
Phase 9-3 데모 데이터 시딩 스크립트

용도: docker-compose.prod.yml 환경(로컬 개발 DB와 분리) 위에서 "실제
서비스처럼 보이는" 데모 데이터를 생성한다. 규모/방식 선택 이유는
docs/decision-log.md "Phase 9-3 데모 데이터 규모" 항목 참고.

- 유저/친구/채팅방 → 실제 REST API 경로로 생성 (비즈니스 로직 그대로 탐)
- 채팅 메시지 → 규모가 커서 REST/WebSocket이 아니라 SQL 직접 삽입
  (Kafka 경로는 안 탐 — optimization-log.md [10]번 벤치마크와 동일 방식)

사전 준비:
  1. docker-compose.prod.yml 환경이 떠 있어야 함 (MySQL 포함)
  2. 그 환경을 바라보는 서버가 8080에 떠 있어야 함 (./gradlew bootRun)
  3. pip install -r scripts/requirements.txt

실행:
  python3 scripts/seed_demo_data.py
"""

import os
import random
import time
from collections import defaultdict
from datetime import datetime, timedelta

import pymysql
import requests

BASE_URL = "http://localhost:8080"
DB_CONFIG = dict(host="127.0.0.1", port=3306, user="root", password=None, database="noltok")

NUM_USERS = 300
FRIENDS_PER_USER_AVG = 8
ROOM_RATIO = 0.45  # 유저 수 대비 채팅방 비율 (40~50% 중간값)
MESSAGES_PER_ROOM_AVG = 100
PASSWORD = "Demo1234"

ADJECTIVES = ["행복한", "즐거운", "용감한", "차분한", "빠른", "느긋한", "귀여운",
              "씩씩한", "다정한", "엉뚱한", "든든한", "상냥한", "재빠른", "포근한"]
NOUNS = ["고양이", "강아지", "여우", "토끼", "다람쥐", "펭귄", "곰돌이",
         "호랑이", "사슴", "부엉이", "고래", "수달", "너구리", "판다"]
SAMPLE_MESSAGES = [
    "안녕하세요!", "오늘 뭐 하세요?", "밥 먹었어요?", "ㅋㅋㅋㅋㅋ", "그거 완전 웃기다",
    "이거 한번 보세요", "오늘 날씨 진짜 좋네요", "주말에 뭐 하실 거예요?",
    "저도 그렇게 생각해요", "진짜요?", "대박이다", "완전 공감", "고생하셨습니다",
    "다음에 또 얘기해요", "넵넵", "알겠습니다!", "확인했어요", "감사합니다 :)",
    "오케이!", "좋은 생각이네요", "그건 좀 아닌 것 같은데요", "다시 한번 볼게요",
    "지금 바로 갈게요", "조금만 기다려주세요", "완료했습니다", "수고하세요~",
]


def signup_and_login(idx, session):
    email = f"demo{idx:04d}@noltok-demo.com"
    nickname = f"{random.choice(ADJECTIVES)}{random.choice(NOUNS)}{idx:03d}"[:10]
    r = session.post(f"{BASE_URL}/api/v1/auth/signup",
                      json={"email": email, "password": PASSWORD, "nickname": nickname})
    if not r.ok:
        print(f"  [signup 실패] idx={idx} status={r.status_code} body={r.text[:200]}")
        return None
    user_id = r.json()["data"]["userId"]

    r = session.post(f"{BASE_URL}/api/v1/auth/login",
                      json={"email": email, "password": PASSWORD})
    if not r.ok:
        print(f"  [login 실패] idx={idx} status={r.status_code} body={r.text[:200]}")
        return None
    token = r.json()["data"]["accessToken"]

    return {"id": user_id, "email": email, "nickname": nickname, "token": token}


def auth(user):
    return {"Authorization": f"Bearer {user['token']}"}


def create_friend_pairs(users, session):
    n = len(users)
    target_pairs = n * FRIENDS_PER_USER_AVG // 2
    pairs = set()
    attempts = 0
    while len(pairs) < target_pairs and attempts < target_pairs * 5:
        a, b = random.sample(range(n), 2)
        pairs.add(tuple(sorted((a, b))))
        attempts += 1

    friend_map = defaultdict(list)
    ok_count = 0
    fail_count = 0
    for a, b in pairs:
        requester, receiver = users[a], users[b]
        try:
            r = session.post(f"{BASE_URL}/api/v1/friends/request", headers=auth(requester),
                              json={"nickname": receiver["nickname"]})
            if not r.ok:
                fail_count += 1
                if fail_count <= 5:
                    print(f"  [friend request 실패] status={r.status_code} body={r.text[:200]}")
                continue
            friend_id = r.json()["data"]["friendId"]
            r2 = session.patch(f"{BASE_URL}/api/v1/friends/{friend_id}/accept", headers=auth(receiver))
            if not r2.ok:
                fail_count += 1
                if fail_count <= 5:
                    print(f"  [friend accept 실패] status={r2.status_code} body={r2.text[:200]}")
                continue
            friend_map[a].append(b)
            friend_map[b].append(a)
            ok_count += 1
        except requests.RequestException as e:
            fail_count += 1
            if fail_count <= 5:
                print(f"  [friend 예외] {e}")
            continue
    if fail_count:
        print(f"  (실패 {fail_count}건, 상위 5건만 출력)")
    return friend_map, ok_count


def create_rooms(users, friend_map, session):
    n = len(users)
    num_rooms = int(n * ROOM_RATIO)
    rooms = []
    direct_pairs_used = set()
    fail_count = 0

    for i in range(num_rooms):
        creator_idx = random.randrange(n)
        creator = users[creator_idx]
        friends = friend_map.get(creator_idx, [])
        roll = random.random()

        try:
            if roll < 0.5 and friends:
                target_idx = random.choice(friends)
                pair = tuple(sorted((creator_idx, target_idx)))
                if pair in direct_pairs_used:
                    continue
                direct_pairs_used.add(pair)
                r = session.post(f"{BASE_URL}/api/v1/chat/rooms", headers=auth(creator),
                                  json={"type": "DIRECT", "nicknames": [users[target_idx]["nickname"]]})
                members = [creator_idx, target_idx]

            elif roll < 0.85 and friends:
                invite_n = min(len(friends), random.randint(1, 5))
                invite_idxs = random.sample(friends, invite_n)
                r = session.post(f"{BASE_URL}/api/v1/chat/rooms", headers=auth(creator),
                                  json={"roomname": f"{creator['nickname']}의 모임방",
                                        "type": "GROUP",
                                        "nicknames": [users[x]["nickname"] for x in invite_idxs]})
                members = [creator_idx] + invite_idxs

            else:
                r = session.post(f"{BASE_URL}/api/v1/chat/rooms", headers=auth(creator),
                                  json={"roomname": f"오픈채팅방 {i}", "type": "OPEN"})
                members = [creator_idx]

            if not r.ok:
                fail_count += 1
                if fail_count <= 5:
                    print(f"  [room 생성 실패] status={r.status_code} body={r.text[:200]}")
                continue
            room_id = r.json()["data"]["roomId"]

            if roll >= 0.85:
                joiners = random.sample(range(n), random.randint(0, 5))
                for j in joiners:
                    if j == creator_idx:
                        continue
                    jr = session.post(f"{BASE_URL}/api/v1/chat/rooms/{room_id}/join", headers=auth(users[j]))
                    if jr.ok:
                        members.append(j)

            rooms.append({"room_id": room_id, "members": members})
        except requests.RequestException as e:
            fail_count += 1
            if fail_count <= 5:
                print(f"  [room 예외] {e}")
            continue

    if fail_count:
        print(f"  (실패 {fail_count}건, 상위 5건만 출력)")
    return rooms


def seed_messages(rooms, users):
    conn = pymysql.connect(**DB_CONFIG)
    try:
        cursor = conn.cursor()
        now = datetime.now()
        rows = []
        for room in rooms:
            n_msgs = max(1, int(random.gauss(MESSAGES_PER_ROOM_AVG, 20)))
            room_start = now - timedelta(days=random.randint(1, 30))
            t = room_start
            for _ in range(n_msgs):
                sender_idx = random.choice(room["members"])
                sender_id = users[sender_idx]["id"]
                content = random.choice(SAMPLE_MESSAGES)
                t = t + timedelta(minutes=random.uniform(1, 25))
                rows.append((room["room_id"], sender_id, content, "TEXT", None, t))

        cursor.executemany(
            "INSERT INTO chat_messages (room_id, sender_id, content, type, file_url, created_at) "
            "VALUES (%s, %s, %s, %s, %s, %s)",
            rows,
        )
        conn.commit()
        return len(rows)
    finally:
        conn.close()


def main():
    DB_CONFIG["password"] = os.environ.get("DB_PASSWORD")
    if not DB_CONFIG["password"]:
        raise SystemExit("DB_PASSWORD 환경변수가 필요합니다 (docker-compose.prod.yml의 MYSQL_ROOT_PASSWORD와 동일한 값).")

    t0 = time.time()
    session = requests.Session()

    print(f"[1/4] 유저 {NUM_USERS}명 생성 중...")
    users = []
    for i in range(NUM_USERS):
        u = signup_and_login(i, session)
        if u:
            users.append(u)
        if (i + 1) % 50 == 0:
            print(f"  {i + 1}/{NUM_USERS}")
    print(f"  완료: {len(users)}명 ({time.time() - t0:.0f}s)")

    print(f"[2/4] 친구 관계 생성 중 (유저당 평균 {FRIENDS_PER_USER_AVG}명)...")
    friend_map, friend_count = create_friend_pairs(users, session)
    print(f"  완료: {friend_count}건 ({time.time() - t0:.0f}s)")

    print(f"[3/4] 채팅방 생성 중 (유저 수의 {int(ROOM_RATIO * 100)}%)...")
    rooms = create_rooms(users, friend_map, session)
    print(f"  완료: {len(rooms)}개 방 ({time.time() - t0:.0f}s)")

    print(f"[4/4] 메시지 SQL 직접 삽입 중 (방당 평균 {MESSAGES_PER_ROOM_AVG}개)...")
    msg_count = seed_messages(rooms, users)
    print(f"  완료: {msg_count}건 ({time.time() - t0:.0f}s)")

    print(f"\n전체 완료 — 유저 {len(users)}명 / 친구관계 {friend_count}건 / "
          f"채팅방 {len(rooms)}개 / 메시지 {msg_count}건 (총 {time.time() - t0:.0f}초)")


if __name__ == "__main__":
    main()
