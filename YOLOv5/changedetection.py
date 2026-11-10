"""
changedetection.py - YOLOv5 Edge Client (공통평가 02)

강의자료 ch08 [8~10 페이지]의 ChangeDetection 클래스를 기반으로 구현.
  - 객체 출현(과거 0 -> 현재 1) 변화가 감지된 프레임만 Django 서버(PhotoBlogServer)로 전송

[강의 코드 대비 수정 사항]
  1) Authorization 헤더: 'JWT <token>' -> 'Token <token>'
     (서버가 DRF TokenAuthentication 을 사용하므로 'Token' 접두어여야 인증됨)
  2) 접속 정보(HOST / username / password)를 환경변수로 덮어쓸 수 있게 하여
     비밀번호를 소스코드에 하드코딩하지 않아도 되도록 함
  3) created_date 를 ISO-8601 문자열로 전송

[편의 기능 2 - 오프라인 재전송 큐 (Offline Store & Forward)]
  - 검출 이미지를 먼저 로컬 큐(edge_queue/)에 저장한 뒤 백그라운드 스레드가 서버로 전송
  - 서버가 꺼져 있거나 네트워크가 끊기면 큐에 보관하고, 주기적으로 재시도하여 복구 시 자동 전송
  - 프로그램을 재시작해도 큐 파일이 남아 있으므로 미전송 데이터가 유실되지 않음
  - 각 검출 건에 고유 uid 를 부여해 재전송 시 서버에서 중복 게시물이 생기지 않도록 함
  - 네트워크 전송을 별도 스레드에서 처리하므로 YOLO 추론 루프가 네트워크 지연으로 멈추지 않음
"""

import json
import os
import pathlib
import threading
import time
import uuid
from datetime import datetime

import cv2
import requests


class ChangeDetection:
    result_prev = []
    HOST = os.environ.get("PHOTOBLOG_HOST", "http://127.0.0.1:8000").rstrip("/")
    username = os.environ.get("PHOTOBLOG_USERNAME", "admin")
    password = os.environ.get("PHOTOBLOG_PASSWORD", "password")
    token = os.environ.get("PHOTOBLOG_TOKEN", "")
    title = ""
    text = ""

    RETRY_INTERVAL = 10  # 오프라인 재전송 주기(초)
    TIMEOUT = 5  # HTTP 요청 타임아웃(초)

    def __init__(self, names, queue_dir=None):
        self.result_prev = [0 for i in range(len(names))]

        # 오프라인 재전송 큐 폴더 (YOLOv5/edge_queue)
        self.queue_dir = pathlib.Path(queue_dir or pathlib.Path(__file__).resolve().parent / "edge_queue")
        self.queue_dir.mkdir(parents=True, exist_ok=True)

        self._lock = threading.Lock()
        self._wakeup = threading.Event()
        self._online = None  # None: 미확인, True: 온라인, False: 오프라인

        # 강의 코드와 동일하게 시작 시 토큰 발급 시도.
        # 단, 서버에 연결할 수 없어도 프로그램이 종료되지 않고 오프라인 모드로 시작함.
        if not self.token:
            self._fetch_token()
        print(f"[ChangeDetection] HOST={self.HOST}, token={'OK' if self.token else '없음(오프라인)'}")

        pending = self.pending_count()
        if pending:
            print(f"[ChangeDetection] 이전 실행에서 전송하지 못한 {pending}건을 재전송합니다.")

        # 백그라운드 전송 스레드 시작
        self._worker = threading.Thread(target=self._worker_loop, name="ChangeDetectionSender", daemon=True)
        self._worker.start()
        self._wakeup.set()

    # ------------------------------------------------------------------ 인증
    def _fetch_token(self):
        try:
            res = requests.post(
                self.HOST + "/api-token-auth/",
                {"username": self.username, "password": self.password},
                timeout=self.TIMEOUT,
            )
            res.raise_for_status()
            self.token = res.json()["token"]  # 토큰 저장
            print(self.token)
            return True
        except requests.RequestException as e:
            if self._online is not False:  # 오프라인 상태에서 재시도할 때는 로그를 반복 출력하지 않음
                print(f"[ChangeDetection] 토큰 발급 실패 -> 오프라인 모드: {e.__class__.__name__}")
            return False

    # -------------------------------------------------------------- 변화 감지
    def add(self, names, detected_current, save_dir, image):
        self.title = ""
        self.text = ""
        change_flag = 0  # 변화 감지 플래그
        i = 0
        while i < len(self.result_prev):
            if self.result_prev[i] == 0 and detected_current[i] == 1:
                change_flag = 1
                self.title = names[i]
                self.text += names[i] + ", "
            i += 1

        self.result_prev = detected_current[:]  # 객체 검출 상태 저장

        if change_flag == 1:
            self.send(save_dir, image)

    # ------------------------------------------------------------------ 전송
    def send(self, save_dir, image):
        now = datetime.now().astimezone()
        today = now

        # 강의 코드와 동일한 경로에 검출 이미지 저장: runs/detect/expN/detected/년/월/일/
        save_path = pathlib.Path(os.getcwd()) / save_dir / "detected" / str(today.year) / str(today.month) / str(today.day)
        save_path.mkdir(parents=True, exist_ok=True)
        full_path = save_path / "{0}-{1}-{2}-{3}.jpg".format(today.hour, today.minute, today.second, today.microsecond)

        dst = cv2.resize(image, dsize=(320, 240), interpolation=cv2.INTER_AREA)
        cv2.imwrite(str(full_path), dst)

        # 큐에 등록 (이미지 + 메타데이터 JSON). 실제 전송은 백그라운드 스레드가 담당.
        uid = uuid.uuid4().hex
        item_image = self.queue_dir / f"{now.strftime('%Y%m%d%H%M%S%f')}_{uid}.jpg"
        cv2.imwrite(str(item_image), dst)
        meta = {
            "uid": uid,
            "title": self.title,
            "text": self.text,
            "created_date": now.isoformat(),
            "published_date": now.isoformat(),
            "image": item_image.name,
        }
        item_image.with_suffix(".json").write_text(json.dumps(meta, ensure_ascii=False), encoding="utf-8")
        print(f"[ChangeDetection] 검출: {self.text.rstrip(', ')} -> 전송 대기열 등록 (대기 {self.pending_count()}건)")
        self._wakeup.set()

    def pending_count(self):
        return len(list(self.queue_dir.glob("*.json")))

    def _post_item(self, meta_path):
        meta = json.loads(meta_path.read_text(encoding="utf-8"))
        image_path = meta_path.with_suffix(".jpg")

        # 인증이 필요한 요청에 아래의 headers를 붙임 (DRF TokenAuthentication)
        headers = {"Authorization": "Token " + self.token, "Accept": "application/json"}
        data = {k: meta[k] for k in ("uid", "title", "text", "created_date", "published_date")}

        with open(image_path, "rb") as f:
            file = {"image": (image_path.name, f, "image/jpeg")}
            res = requests.post(
                self.HOST + "/api_root/Post/", data=data, files=file, headers=headers, timeout=self.TIMEOUT
            )
        print(res)

        if res.status_code in (200, 201):  # 201: 신규 생성, 200: 이미 등록된 uid (중복 방지)
            meta_path.unlink(missing_ok=True)
            image_path.unlink(missing_ok=True)
            return True
        if res.status_code == 401:  # 토큰 만료/무효 -> 재발급 후 재시도
            self.token = ""
        elif res.status_code == 400:  # 데이터 오류는 재시도해도 실패하므로 별도 폴더로 이동
            failed = self.queue_dir / "failed"
            failed.mkdir(exist_ok=True)
            meta_path.rename(failed / meta_path.name)
            image_path.rename(failed / image_path.name)
            print(f"[ChangeDetection] 400 응답 -> failed/ 로 이동: {res.text[:200]}")
        return False

    def flush(self):
        """대기열의 모든 항목을 오래된 순서대로 전송한다. 성공 개수를 반환."""
        sent = 0
        with self._lock:
            for meta_path in sorted(self.queue_dir.glob("*.json")):
                if not self.token and not self._fetch_token():
                    self._set_online(False)
                    break
                try:
                    if self._post_item(meta_path):
                        sent += 1
                        self._set_online(True)
                    # 실패(401 등)한 항목은 큐에 남아 다음 주기에 다시 전송됨
                except requests.RequestException as e:
                    self._set_online(False, e)
                    break
        return sent

    def _set_online(self, online, err=None):
        if online != self._online:
            if online:
                print(f"[ChangeDetection] 서버 연결됨 - 대기 중이던 데이터를 전송합니다.")
            else:
                reason = f" ({err.__class__.__name__})" if err else ""
                print(f"[ChangeDetection] 서버 연결 실패{reason} - 오프라인 큐에 보관, {self.RETRY_INTERVAL}초 후 재시도")
            self._online = online

    def _worker_loop(self):
        while True:
            self._wakeup.wait(timeout=self.RETRY_INTERVAL)
            self._wakeup.clear()
            if self.pending_count():
                sent = self.flush()
                if sent:
                    print(f"[ChangeDetection] {sent}건 전송 완료 (남은 대기 {self.pending_count()}건)")

    def wait_until_flushed(self, timeout=30):
        """프로그램 종료 직전에 대기열을 최대 timeout 초 동안 비우도록 시도."""
        deadline = time.time() + timeout
        while self.pending_count() and time.time() < deadline:
            self._wakeup.set()
            time.sleep(0.5)
        return self.pending_count() == 0
