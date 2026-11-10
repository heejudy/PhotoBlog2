
package com.example.photoviewer;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView textView;
    private TextView textCacheInfo;
    private EditText editSearch;
    private Spinner spinnerSort;
    private Spinner spinnerViewMode;
    private RecyclerView recyclerView;

    private final String site_url = "https://heeju1223.pythonanywhere.com";

    private final String token = BuildConfig.DJANGO_API_TOKEN;

    private CloadImage taskDownload;

    // 편의 기능 1: 메모리 + 디스크 2단계 이미지 캐시 / 오프라인 모드
    private ImageCache imageCache;

    private final List<PhotoPost> allPosts = new ArrayList<>();

    private static class PhotoPost {

        Bitmap image;
        String title;
        String text;
        String createdDate;

        PhotoPost(Bitmap image, String title,
                  String text, String createdDate) {

            this.image = image;
            this.title = title;
            this.text = text;
            this.createdDate = createdDate;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.textView);
        textCacheInfo = findViewById(R.id.textCacheInfo);
        imageCache = new ImageCache(getApplicationContext());
        editSearch = findViewById(R.id.editSearch);
        spinnerSort = findViewById(R.id.spinnerSort);
        spinnerViewMode = findViewById(R.id.spinnerViewMode);
        recyclerView = findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(
                new LinearLayoutManager(this)
        );

        String[] sortOptions = {
                "최신순", "오래된순"
        };

        ArrayAdapter<String> sortAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        sortOptions
                );

        sortAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerSort.setAdapter(sortAdapter);

        spinnerSort.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {
                        updatePostList();
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent) {
                    }
                }
        );

        String[] viewOptions = {
                "제목형", "목록형", "이미지형"
        };

        ArrayAdapter<String> viewAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        viewOptions
                );

        viewAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerViewMode.setAdapter(viewAdapter);

        spinnerViewMode.setSelection(
                ImageAdapter.MODE_IMAGE
        );

        spinnerViewMode.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {
                        updatePostList();
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent) {
                    }
                }
        );

        editSearch.addTextChangedListener(
                new TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count) {
                        updatePostList();
                    }

                    @Override
                    public void afterTextChanged(
                            Editable s) {
                    }
                }
        );
    
        // 편의 기능 1: 동기화 버튼을 길게 누르면 캐시 삭제 (동작 시연용)
        findViewById(R.id.btn_load).setOnLongClickListener(v -> {
            imageCache.clear();
            allPosts.clear();
            updatePostList();
            textView.setText("캐시를 삭제했습니다. 동기화를 눌러 다시 받아오세요.");
            updateCacheInfo();
            Toast.makeText(this, "이미지 캐시 삭제", Toast.LENGTH_SHORT).show();
            return true;
        });

        // 편의 기능 1: 앱 시작 시 서버 연결 없이 캐시된 마지막 목록을 즉시 표시
        new CacheLoader().execute();
    }

    public void onClickDownload(View v) {

        if (taskDownload != null
                && taskDownload.getStatus()
                == AsyncTask.Status.RUNNING) {

            taskDownload.cancel(true);
        }

        textView.setText("서버에서 이미지 다운로드 중...");

        taskDownload = new CloadImage();

        taskDownload.execute(
                site_url + "/api_root/Post/"
        );
    }

    public void onClickUpload(View v) {

        Intent intent =
                new Intent(this, UploadActivity.class);

        startActivity(intent);
    }

    private void updatePostList() {

        String keyword = editSearch.getText()
                .toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        List<PhotoPost> filteredPosts = new ArrayList<>();

        for (PhotoPost post : allPosts) {

            String title =
                    post.title.toLowerCase(Locale.ROOT);

            String content =
                    post.text.toLowerCase(Locale.ROOT);

            if (title.contains(keyword)
                    || content.contains(keyword)) {

                filteredPosts.add(post);
            }
        }

        Collections.sort(
                filteredPosts,
                (post1, post2) -> {

                    if (spinnerSort.getSelectedItemPosition() == 0) {

                        return post2.createdDate.compareTo(
                                post1.createdDate
                        );

                    } else {

                        return post1.createdDate.compareTo(
                                post2.createdDate
                        );
                    }
                }
        );

        List<ImageAdapter.ImageItem> displayItems =
                new ArrayList<>();

        for (PhotoPost post : filteredPosts) {

            displayItems.add(
                    new ImageAdapter.ImageItem(
                            post.image,
                            post.title,
                            post.text,
                            post.createdDate
                    )
            );
        }

        int selectedMode =
                spinnerViewMode.getSelectedItemPosition();

        if (selectedMode < 0) {
            selectedMode = ImageAdapter.MODE_IMAGE;
        }

        recyclerView.setAdapter(
                new ImageAdapter(displayItems, selectedMode)
        );

        textView.setText(
                "게시물: " + filteredPosts.size() + "개"
        );
    }

    /** 캐시 상태 표시 (메모리/디스크 적중 수, 디스크 사용량, 마지막 동기화 시각) */
    private void updateCacheInfo() {
        long last = imageCache.getLastSyncMillis();
        String lastText = last == 0 ? "없음" :
                new SimpleDateFormat("MM-dd HH:mm:ss", Locale.KOREA).format(new Date(last));
        textCacheInfo.setText(String.format(Locale.KOREA,
                "캐시: 메모리 %d · 디스크 %d · 다운로드 %d | 저장 %d개(%.1fKB) | 마지막 동기화 %s",
                imageCache.memoryHits, imageCache.diskHits, imageCache.networkLoads,
                imageCache.diskFileCount(), imageCache.diskSizeBytes() / 1024.0, lastText));
    }

    /** 게시물 목록 JSON -> PhotoPost 목록. offlineOnly=true 이면 캐시에 있는 이미지만 사용 */
    private List<PhotoPost> parsePosts(String json, boolean offlineOnly,
                                       AsyncTask<?, ?, ?> task) throws JSONException {
        List<PhotoPost> postList = new ArrayList<>();
        JSONArray aryJson = new JSONArray(json);

        for (int i = 0; i < aryJson.length(); i++) {
            if (task != null && task.isCancelled()) {
                break;
            }
            JSONObject postJson = aryJson.getJSONObject(i);
            String imageUrl = postJson.optString("image", "");
            if (imageUrl.isEmpty() || imageUrl.equals("null")) {
                continue;
            }

            String imagePath;
            try {
                // 서버가 내려준 이미지 URL 의 경로(/media/...)만 사용 -> 캐시 키 겸 다운로드 경로
                imagePath = new URL(new URL(site_url), imageUrl).getFile();
            } catch (IOException e) {
                continue;
            }

            Bitmap bitmap = imageCache.get(imagePath);   // 1) 메모리 -> 2) 디스크
            if (bitmap == null && !offlineOnly) {
                bitmap = downloadImage(site_url + imagePath, imagePath); // 3) 네트워크
            }
            if (bitmap != null) {
                postList.add(new PhotoPost(
                        bitmap,
                        postJson.optString("title", ""),
                        postJson.optString("text", ""),
                        postJson.optString("created_date", "")
                ));
            }
        }
        return postList;
    }

    private Bitmap downloadImage(String downloadUrl, String cacheKey) {
        HttpURLConnection imageConn = null;
        try {
            imageConn = (HttpURLConnection) new URL(downloadUrl).openConnection();
            imageConn.setConnectTimeout(10000);
            imageConn.setReadTimeout(10000);
            imageConn.setRequestProperty("Authorization", "Token " + token);

            try (InputStream imgStream = imageConn.getInputStream()) {
                return imageCache.put(cacheKey, readAll(imgStream));
            }
        } catch (IOException e) {
            Log.e("PhotoViewer", "이미지 다운로드 오류", e);
            return null;
        } finally {
            if (imageConn != null) {
                imageConn.disconnect();
            }
        }
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = is.read(buffer)) != -1) {
            bos.write(buffer, 0, length);
        }
        return bos.toByteArray();
    }

    private String lastSyncText() {
        long last = imageCache.getLastSyncMillis();
        return last == 0 ? "-" :
                new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(new Date(last));
    }

    /** 앱 시작 시 캐시만으로 목록 복원 (네트워크 사용 안 함) */
    private class CacheLoader extends AsyncTask<Void, Void, List<PhotoPost>> {
        @Override
        protected List<PhotoPost> doInBackground(Void... voids) {
            String json = imageCache.loadPostsJson();
            if (json == null) {
                return null;
            }
            try {
                return parsePosts(json, true, this);
            } catch (JSONException e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<PhotoPost> posts) {
            if (posts != null && !posts.isEmpty() && allPosts.isEmpty()) {
                allPosts.addAll(posts);
                updatePostList();
                textView.setText("캐시에서 " + posts.size() + "개 표시 (마지막 동기화 "
                        + lastSyncText() + ") - 최신 내용은 동기화를 누르세요.");
            }
            updateCacheInfo();
        }
    }

    private class CloadImage
            extends AsyncTask<String, Integer, List<PhotoPost>> {

        private String errorMessage = null;
        private boolean offline = false;

        @Override
        protected void onPreExecute() {
            imageCache.resetStats();
        }

        @Override
        protected List<PhotoPost> doInBackground(String... urls) {
            HttpURLConnection conn = null;
            try {
                URL urlAPI = new URL(urls[0]);
                conn = (HttpURLConnection) urlAPI.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                Log.d("PhotoViewer", "API 응답 코드: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    String json;
                    try (InputStream is = conn.getInputStream()) {
                        json = new String(readAll(is), java.nio.charset.StandardCharsets.UTF_8);
                    }
                    List<PhotoPost> posts = parsePosts(json, false, this);
                    imageCache.savePostsJson(json);   // 성공한 목록을 오프라인용으로 저장
                    return posts;
                }
                errorMessage = "API 오류: HTTP " + responseCode;

            } catch (IOException e) {
                // 네트워크 단절 / 서버 다운 -> 오프라인 모드로 캐시 사용
                offline = true;
                errorMessage = e.getClass().getSimpleName();
                Log.e("PhotoViewer", "서버 연결 실패 -> 오프라인 모드", e);
            } catch (JSONException e) {
                errorMessage = "JSON 오류: " + e.getMessage();
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }

            if (offline) {
                String cached = imageCache.loadPostsJson();
                if (cached != null) {
                    try {
                        return parsePosts(cached, true, this);
                    } catch (JSONException ignored) {
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<PhotoPost> posts) {
            if (posts == null) {
                textView.setText("동기화 실패: " + errorMessage
                        + (offline ? " (저장된 캐시 없음)" : ""));
                updateCacheInfo();
                return;
            }

            allPosts.clear();
            allPosts.addAll(posts);
            updatePostList();

            if (offline) {
                textView.setText("⚠ 오프라인 모드 - 서버 연결 실패, 마지막 동기화("
                        + lastSyncText() + ") 캐시 " + posts.size() + "개 표시");
                Toast.makeText(MainActivity.this,
                        "오프라인: 캐시된 이미지를 표시합니다.", Toast.LENGTH_LONG).show();
            } else {
                textView.setText("동기화 완료: 게시물 " + posts.size() + "개 (캐시 사용 "
                        + (imageCache.memoryHits + imageCache.diskHits)
                        + " · 새로 다운로드 " + imageCache.networkLoads + ")");
                Toast.makeText(MainActivity.this, "동기화 완료!", Toast.LENGTH_SHORT).show();
            }
            updateCacheInfo();
        }
    }
}
