
package com.example.photoviewer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class UploadActivity extends AppCompatActivity {

    EditText editTitle, editContent;
    ImageView imagePreview;
    Uri selectedImageUri;

    String site_url = "https://heeju1223.pythonanywhere.com";

    String token = BuildConfig.DJANGO_API_TOKEN;

    ActivityResultLauncher<String> imagePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        editTitle = findViewById(R.id.editTitle);
        editContent = findViewById(R.id.editContent);
        imagePreview = findViewById(R.id.imagePreview);

        Button btnSelectImage = findViewById(R.id.btnSelectImage);
        Button btnSubmitPost = findViewById(R.id.btnSubmitPost);

        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        imagePreview.setImageURI(uri);
                    }
                }
        );

        btnSelectImage.setOnClickListener(
                v -> imagePicker.launch("image/*")
        );

        btnSubmitPost.setOnClickListener(v -> {
            String title = editTitle.getText().toString().trim();
            String content = editContent.getText().toString().trim();

            if (title.isEmpty()) {
                editTitle.setError("제목을 입력하세요.");
                return;
            }

            if (content.isEmpty()) {
                editContent.setError("내용을 입력하세요.");
                return;
            }

            if (selectedImageUri == null) {
                Toast.makeText(
                        this,
                        "이미지를 선택하세요.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            btnSubmitPost.setEnabled(false);

            new PutPost(btnSubmitPost).execute(title, content);
        });
    }

    private class PutPost extends AsyncTask<String, Void, String> {

        Button submitButton;
        boolean success = false;

        PutPost(Button button) {
            submitButton = button;
        }

        @Override
        protected String doInBackground(String... values) {

            String title = values[0];
            String content = values[1];

            HttpURLConnection conn = null;

            try {
                URL url = new URL(site_url + "/api_root/Post/");

                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                conn.setRequestProperty(
                        "Authorization",
                        "Token " + token
                );

                String boundary = "Boundary" + UUID.randomUUID();

                conn.setRequestProperty(
                        "Content-Type",
                        "multipart/form-data; boundary=" + boundary
                );

                try (OutputStream os = conn.getOutputStream()) {

                    writeTextField(os, boundary, "title", title);
                    writeTextField(os, boundary, "text", content);

                    String mimeType =
                            getContentResolver().getType(selectedImageUri);

                    if (mimeType == null
                            || !mimeType.startsWith("image/")) {
                        mimeType = "image/jpeg";
                    }

                    String extension = "jpg";

                    if (mimeType.equals("image/png")) {
                        extension = "png";
                    } else if (mimeType.equals("image/webp")) {
                        extension = "webp";
                    } else if (mimeType.equals("image/gif")) {
                        extension = "gif";
                    }

                    String filename = "upload." + extension;

                    String imageHeader =
                            "--" + boundary + "\r\n"
                                    + "Content-Disposition: form-data; "
                                    + "name=\"image\"; filename=\""
                                    + filename + "\"\r\n"
                                    + "Content-Type: " + mimeType
                                    + "\r\n\r\n";

                    os.write(imageHeader.getBytes(
                            StandardCharsets.UTF_8
                    ));

                    try (InputStream is =
                                 getContentResolver()
                                         .openInputStream(selectedImageUri)) {

                        if (is == null) {
                            return "이미지 파일을 읽을 수 없습니다.";
                        }

                        byte[] buffer = new byte[8192];
                        int length;

                        while ((length = is.read(buffer)) != -1) {
                            os.write(buffer, 0, length);
                        }
                    }

                    os.write("\r\n".getBytes(
                            StandardCharsets.UTF_8
                    ));

                    os.write(
                            ("--" + boundary + "--\r\n")
                                    .getBytes(StandardCharsets.UTF_8)
                    );
                }

                int responseCode = conn.getResponseCode();

                if (responseCode == 201
                        || responseCode == 200) {
                    success = true;
                    return "게시물 업로드 성공!";
                }

                StringBuilder error = new StringBuilder();
                InputStream errorStream = conn.getErrorStream();

                if (errorStream != null) {
                    try (InputStream is = errorStream) {
                        byte[] buffer = new byte[4096];
                        int length;

                        while ((length = is.read(buffer)) != -1) {
                            error.append(
                                    new String(
                                            buffer, 0, length,
                                            StandardCharsets.UTF_8
                                    )
                            );
                        }
                    }
                }

                return "업로드 실패: HTTP "
                        + responseCode + " " + error;

            } catch (Exception e) {
                return "업로드 오류: " + e.getMessage();

            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        private void writeTextField(
                OutputStream os,
                String boundary,
                String name,
                String value
        ) throws IOException {

            String part =
                    "--" + boundary + "\r\n"
                            + "Content-Disposition: form-data; name=\""
                            + name + "\"\r\n"
                            + "Content-Type: text/plain; charset=UTF-8\r\n\r\n"
                            + value + "\r\n";

            os.write(part.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        protected void onPostExecute(String result) {

            submitButton.setEnabled(true);

            Toast.makeText(
                    UploadActivity.this,
                    result,
                    Toast.LENGTH_LONG
            ).show();

            if (success) {
                setResult(RESULT_OK);
                finish();
            }
        }
    }
}
