from blog.models import Post
from rest_framework import serializers


class PostSerializer(serializers.ModelSerializer):
    class Meta:
        model = Post
        fields = (
            'id',
            'uid',
            'author',
            'title',
            'text',
            'created_date',
            'published_date',
            'image'
        )
        # created_date 는 쓰기 허용: 오프라인 재전송 시에도 "실제 검출 시각"이 유지되도록 함
        read_only_fields = ('id', 'author')
        extra_kwargs = {
            # 중복 검사는 ViewSet.create 에서 200 응답으로 처리하므로 기본 unique 검증은 제외
            'uid': {'validators': [], 'required': False, 'allow_null': True},
        }
