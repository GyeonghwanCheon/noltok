"""
Phase 9-3-2 부하 테스트 준비 스크립트

scripts/seed_demo_data.py로 만든 데모 데이터(유저 300/채팅방 131)에서
"채팅방 + 그 방의 실제 멤버" 쌍을 뽑아, 각 멤버로 로그인해 JWT를 발급받고
scripts/load_test_data.json에 저장한다. k6 스크립트(load_test_chat.js)가
이 파일을 읽어서 각 가상 유저(VU)가 실제로 속한 방에 실제로 로그인한
계정으로 접속하도록 한다 (권한 없는 방에 SEND해서 실패하는 걸 방지).

사전 준비: docker-compose.prod.yml 환경 + 그 위의 서버가 떠 있어야 함,
           scripts/seed_demo_data.py가 이미 실행된 상태여야 함

실행: python3 scripts/prepare_load_test_data.py [--count 100]
"""

import argparse
import json
import os

import pymysql
import requests

BASE_URL = "http://localhost:8080"
DB_CONFIG = dict(host="127.0.0.1", port=3306, user="root", password=None, database="noltok")
PASSWORD = "Demo1234"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", type=int, default=100)
    parser.add_argument("--out", default="scripts/load_test_data.json")
    args = parser.parse_args()

    DB_CONFIG["password"] = os.environ.get("DB_PASSWORD")
    if not DB_CONFIG["password"]:
        raise SystemExit("DB_PASSWORD 환경변수가 필요합니다.")

    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()
    # 방마다 멤버 1명을 랜덤하게 뽑음 (room_id, user_id, email)
    cursor.execute("""
        SELECT r.room_id, r.user_id, u.email
        FROM (
            SELECT crm.room_id, crm.user_id,
                   ROW_NUMBER() OVER (PARTITION BY crm.room_id ORDER BY RAND()) AS rn
            FROM chat_room_members crm
            JOIN chat_rooms cr ON cr.id = crm.room_id
            WHERE cr.is_active = 1 AND crm.is_active = 1
        ) r
        JOIN users u ON u.id = r.user_id
        WHERE r.rn = 1
        ORDER BY RAND()
        LIMIT %s
    """, (args.count,))
    pairs = cursor.fetchall()
    conn.close()

    print(f"방-멤버 쌍 {len(pairs)}개 추출, 로그인 중...")
    session = requests.Session()
    entries = []
    for room_id, user_id, email in pairs:
        r = session.post(f"{BASE_URL}/api/v1/auth/login", json={"email": email, "password": PASSWORD})
        if not r.ok:
            print(f"  로그인 실패: {email} ({r.status_code})")
            continue
        token = r.json()["data"]["accessToken"]
        entries.append({"roomId": room_id, "userId": user_id, "token": token})

    with open(args.out, "w") as f:
        json.dump(entries, f)

    print(f"완료: {len(entries)}건 → {args.out}")


if __name__ == "__main__":
    main()
