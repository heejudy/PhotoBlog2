package com.example.photoviewer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * 편의 기능 1 - 2단계 이미지 캐시 + 오프라인 모드 (공통평가 02)
 *
 * 1단계: 메모리 캐시 (LruCache) - 앱 실행 중 같은 이미지를 다시 디코딩/다운로드하지 않음
 * 2단계: 디스크 캐시 (앱 전용 cacheDir) - 앱을 껐다 켜도, 네트워크가 끊겨도 이미지 표시
 * + 게시물 목록(JSON)도 마지막 동기화 결과를 파일로 저장 -> 오프라인 시 목록 복원
 *
 * 캐시 키: 이미지 URL 의 경로(/media/...)를 SHA-256 으로 해시한 파일명
 *  (서버 주소가 10.0.2.2 / pythonanywhere 로 바뀌어도 같은 이미지는 같은 키)
 */
public class ImageCache {

    private static final String TAG = "ImageCache";
    private static final long MAX_DISK_BYTES = 50L * 1024 * 1024; // 디스크 캐시 최대 50MB
    private static final String PREFS = "photo_cache";
    private static final String KEY_LAST_SYNC = "last_sync";

    private final LruCache<String, Bitmap> memoryCache;
    private final File diskDir;
    private final File postsFile;
    private final SharedPreferences prefs;

    // 동작 증빙용 통계 (화면에 표시)
    public int memoryHits = 0;
    public int diskHits = 0;
    public int networkLoads = 0;

    public ImageCache(Context context) {
        // 앱 최대 메모리의 1/8 을 메모리 캐시로 사용 (KB 단위)
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        memoryCache = new LruCache<String, Bitmap>(maxKb / 8) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        diskDir = new File(context.getCacheDir(), "image_cache");
        if (!diskDir.exists() && !diskDir.mkdirs()) {
            Log.w(TAG, "디스크 캐시 폴더 생성 실패");
        }
        postsFile = new File(context.getFilesDir(), "posts_cache.json");
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void resetStats() {
        memoryHits = 0;
        diskHits = 0;
        networkLoads = 0;
    }

    /** 메모리 -> 디스크 순으로 찾고, 없으면 null (네트워크에서 받아야 함) */
    public synchronized Bitmap get(String key) {
        Bitmap bitmap = memoryCache.get(key);
        if (bitmap != null) {
            memoryHits++;
            return bitmap;
        }
        File file = fileFor(key);
        if (file.exists()) {
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                memoryCache.put(key, bitmap);
                file.setLastModified(System.currentTimeMillis()); // LRU 갱신
                diskHits++;
                return bitmap;
            }
        }
        return null;
    }

    /** 네트워크에서 받은 원본 바이트를 디스크에 저장하고 디코딩된 Bitmap 을 메모리에 저장 */
    public synchronized Bitmap put(String key, byte[] bytes) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bitmap == null) {
            return null;
        }
        networkLoads++;
        memoryCache.put(key, bitmap);
        try (FileOutputStream fos = new FileOutputStream(fileFor(key))) {
            fos.write(bytes);
        } catch (IOException e) {
            Log.e(TAG, "디스크 캐시 저장 실패", e);
        }
        trimDisk();
        return bitmap;
    }

    /** 마지막으로 성공한 게시물 목록 JSON 저장 */
    public void savePostsJson(String json) {
        try {
            Files.write(postsFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
            prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();
        } catch (IOException e) {
            Log.e(TAG, "게시물 목록 캐시 저장 실패", e);
        }
    }

    /** 저장된 게시물 목록 JSON (없으면 null) */
    public String loadPostsJson() {
        if (!postsFile.exists()) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(postsFile.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public long getLastSyncMillis() {
        return prefs.getLong(KEY_LAST_SYNC, 0);
    }

    /** 캐시 전체 삭제 (동기화 버튼 길게 누르기) */
    public synchronized void clear() {
        memoryCache.evictAll();
        File[] files = diskDir.listFiles();
        if (files != null) {
            for (File f : files) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
        //noinspection ResultOfMethodCallIgnored
        postsFile.delete();
        prefs.edit().remove(KEY_LAST_SYNC).apply();
        resetStats();
    }

    public synchronized long diskSizeBytes() {
        long total = 0;
        File[] files = diskDir.listFiles();
        if (files != null) {
            for (File f : files) {
                total += f.length();
            }
        }
        return total;
    }

    public synchronized int diskFileCount() {
        File[] files = diskDir.listFiles();
        return files == null ? 0 : files.length;
    }

    /** 디스크 캐시가 최대 용량을 넘으면 오래 사용하지 않은 파일부터 삭제 */
    private void trimDisk() {
        File[] files = diskDir.listFiles();
        if (files == null) {
            return;
        }
        long total = 0;
        for (File f : files) {
            total += f.length();
        }
        if (total <= MAX_DISK_BYTES) {
            return;
        }
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : files) {
            if (total <= MAX_DISK_BYTES) {
                break;
            }
            total -= f.length();
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    private File fileFor(String key) {
        return new File(diskDir, sha256(key) + ".img");
    }

    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
