# from django.test import TestCase

# # Create your tests here.

from django.contrib.auth import get_user_model
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase


class PostAPITest(APITestCase):

    def setUp(self):
        User = get_user_model()

        self.admin_user = User.objects.create_superuser(
            username="testadmin",
            email="test@example.com",
            password="TestPassword123!"
        )

        self.token = Token.objects.create(user=self.admin_user)
        self.url = "/api_root/Post/"

    def test_get_without_authentication(self):
        """인증 없이 GET 요청 시 접근 차단"""
        response = self.client.get(self.url)

        self.assertIn(response.status_code, [401, 403])

    def test_get_with_token(self):
        """관리자 토큰으로 GET 요청 시 성공"""
        self.client.credentials(
            HTTP_AUTHORIZATION=f"Token {self.token.key}"
        )

        response = self.client.get(self.url)

        self.assertEqual(response.status_code, 200)
        self.assertIsInstance(response.data, list)

    def test_post_without_authentication(self):
        """인증 없이 POST 요청 시 접근 차단"""
        response = self.client.post(
            self.url,
            {
                "title": "자동 테스트 게시물",
                "text": "API 테스트"
            },
            format="json"
        )

        self.assertIn(response.status_code, [401, 403])

# ---------------------------------------------------------------------------
# 공통평가 02 - YOLO 엣지 클라이언트 업로드 / 편의 기능 2(중복 방지) 테스트
# ---------------------------------------------------------------------------
import io
import shutil
import tempfile

from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import override_settings
from PIL import Image

from blog.models import Post

TEMP_MEDIA = tempfile.mkdtemp()


def make_jpeg(name="detected.jpg"):
    buf = io.BytesIO()
    Image.new("RGB", (320, 240), (200, 50, 50)).save(buf, format="JPEG")
    return SimpleUploadedFile(name, buf.getvalue(), content_type="image/jpeg")


@override_settings(MEDIA_ROOT=TEMP_MEDIA)
class EdgeUploadAPITest(APITestCase):

    @classmethod
    def tearDownClass(cls):
        super().tearDownClass()
        shutil.rmtree(TEMP_MEDIA, ignore_errors=True)

    def setUp(self):
        User = get_user_model()
        self.admin_user = User.objects.create_superuser(
            username="edge", email="edge@example.com", password="EdgePassword123!"
        )
        self.url = "/api_root/Post/"

    def login_with_token_api(self):
        """changedetection.py 와 동일하게 /api-token-auth/ 로 토큰 발급"""
        res = self.client.post(
            "/api-token-auth/",
            {"username": "edge", "password": "EdgePassword123!"},
        )
        self.assertEqual(res.status_code, 200)
        self.client.credentials(HTTP_AUTHORIZATION="Token " + res.data["token"])

    def edge_payload(self, uid="a1b2c3"):
        return {
            "uid": uid,
            "title": "person",
            "text": "person, ",
            "created_date": "2026-11-09T10:00:00+09:00",
            "published_date": "2026-11-09T10:00:00+09:00",
            "image": make_jpeg(),
        }

    def test_edge_upload_creates_post(self):
        """토큰 인증 후 검출 이미지 업로드 -> 201, 작성자는 토큰 사용자"""
        self.login_with_token_api()
        res = self.client.post(self.url, self.edge_payload(), format="multipart")
        self.assertEqual(res.status_code, 201)
        post = Post.objects.get(uid="a1b2c3")
        self.assertEqual(post.author, self.admin_user)
        self.assertEqual(post.title, "person")
        self.assertTrue(post.image.name.endswith(".jpg"))

    def test_jwt_prefix_is_rejected(self):
        """강의 코드의 'JWT <token>' 헤더는 TokenAuthentication 에서 인증되지 않음"""
        token = Token.objects.create(user=self.admin_user)
        self.client.credentials(HTTP_AUTHORIZATION="JWT " + token.key)
        res = self.client.post(self.url, self.edge_payload(), format="multipart")
        self.assertIn(res.status_code, [401, 403])

    def test_duplicate_uid_is_not_created_twice(self):
        """편의 기능 2: 같은 uid 재전송 시 200 + 기존 게시물 반환 (중복 생성 X)"""
        self.login_with_token_api()
        first = self.client.post(self.url, self.edge_payload("dup-1"), format="multipart")
        second = self.client.post(self.url, self.edge_payload("dup-1"), format="multipart")
        self.assertEqual(first.status_code, 201)
        self.assertEqual(second.status_code, 200)
        self.assertEqual(second.data["id"], first.data["id"])
        self.assertEqual(Post.objects.filter(uid="dup-1").count(), 1)

    def test_detection_time_is_preserved(self):
        """오프라인 후 늦게 전송돼도 실제 검출 시각(created_date)이 유지됨"""
        self.login_with_token_api()
        res = self.client.post(self.url, self.edge_payload("late-1"), format="multipart")
        self.assertEqual(res.status_code, 201)
        self.assertTrue(res.data["created_date"].startswith("2026-11-09T10:00:00"))

    def test_list_returns_latest_first(self):
        """목록 API 는 최신 검출 순으로 반환"""
        self.login_with_token_api()
        old = self.edge_payload("old")
        old["created_date"] = "2026-11-01T09:00:00+09:00"
        self.client.post(self.url, old, format="multipart")
        self.client.post(self.url, self.edge_payload("new"), format="multipart")
        res = self.client.get(self.url)
        self.assertEqual([p["uid"] for p in res.data][:2], ["new", "old"])
