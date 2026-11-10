from django.db import models
from django.utils import timezone

class Post(models.Model):
    author = models.ForeignKey('auth.User', on_delete=models.CASCADE)
    title = models.CharField(max_length=200)
    text = models.TextField()
    created_date = models.DateTimeField(default=timezone.now)
    published_date = models.DateTimeField(blank=True, null=True)
    image = models.ImageField(upload_to='images/%Y/%m/%d/', default='images/default_error.png')
    # 편의 기능 2 (서버): 엣지(YOLO) 클라이언트가 부여한 검출 건 고유 ID.
    # 오프라인 재전송 시 같은 uid 가 다시 오면 새 게시물을 만들지 않음 (중복 방지)
    uid = models.CharField(max_length=64, unique=True, null=True, blank=True)

    class Meta:
        ordering = ['-created_date']

    def publish(self):
        self.published_date = timezone.now()
        self.save()

    def __str__(self):
        return self.title