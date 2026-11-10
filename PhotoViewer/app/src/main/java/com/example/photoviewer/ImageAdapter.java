
package com.example.photoviewer;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ImageAdapter
        extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {

    public static final int MODE_TITLE = 0;
    public static final int MODE_LIST = 1;
    public static final int MODE_IMAGE = 2;

    public static class ImageItem {
        public Bitmap image;
        public String title;
        public String text;
        public String createdDate;

        public ImageItem(Bitmap image, String title,
                         String text, String createdDate) {
            this.image = image;
            this.title = title;
            this.text = text;
            this.createdDate = createdDate;
        }
    }

    private final List<ImageItem> imageList;
    private final int viewMode;

    public ImageAdapter(List<ImageItem> imageList, int viewMode) {
        this.imageList = imageList;
        this.viewMode = viewMode;
    }

    private int dp(View view, int value) {
        return (int) (value *
                view.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemViewType(int position) {
        return viewMode;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {

        View view;

        if (viewType == MODE_IMAGE) {

            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_image, parent, false);

        } else {

            LinearLayout layout =
                    new LinearLayout(parent.getContext());

            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setPadding(
                    dp(layout, 12),
                    dp(layout, 12),
                    dp(layout, 12),
                    dp(layout, 12)
            );

            layout.setGravity(
                    android.view.Gravity.CENTER_VERTICAL
            );

            if (viewType == MODE_LIST) {

                ImageView thumbnail =
                        new ImageView(parent.getContext());

                thumbnail.setId(android.R.id.icon);
                thumbnail.setScaleType(
                        ImageView.ScaleType.CENTER_CROP
                );

                LinearLayout.LayoutParams imageParams =
                        new LinearLayout.LayoutParams(
                                dp(layout, 80),
                                dp(layout, 80)
                        );

                imageParams.setMargins(
                        0, 0, dp(layout, 12), 0
                );

                layout.addView(thumbnail, imageParams);
            }

            LinearLayout textLayout =
                    new LinearLayout(parent.getContext());

            textLayout.setOrientation(LinearLayout.VERTICAL);

            TextView titleView =
                    new TextView(parent.getContext());

            titleView.setId(android.R.id.text1);
            titleView.setTextSize(18);
            titleView.setTypeface(
                    null, android.graphics.Typeface.BOLD
            );

            textLayout.addView(titleView);

            if (viewType == MODE_LIST) {

                TextView contentView =
                        new TextView(parent.getContext());

                contentView.setId(android.R.id.text2);
                contentView.setTextSize(14);
                contentView.setMaxLines(2);
                contentView.setEllipsize(
                        android.text.TextUtils.TruncateAt.END
                );

                textLayout.addView(contentView);
            }

            layout.addView(
                    textLayout,
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1
                    )
            );

            view = layout;
        }

        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ImageViewHolder holder, int position) {

        ImageItem item = imageList.get(position);

        if (viewMode == MODE_IMAGE) {

            holder.imageView.setImageBitmap(item.image);

        } else {

            holder.titleView.setText(item.title);

            if (viewMode == MODE_LIST) {
                holder.imageView.setImageBitmap(item.image);
                holder.contentView.setText(item.text);
            }
        }

        holder.itemView.setOnClickListener(
                v -> showDetailDialog(v, item)
        );
    }

    private void showDetailDialog(View v, ImageItem item) {

        Dialog dialog = new Dialog(v.getContext());

        ScrollView scrollView = new ScrollView(v.getContext());

        LinearLayout layout = new LinearLayout(v.getContext());
        layout.setOrientation(LinearLayout.VERTICAL);

        int padding = dp(v, 20);
        layout.setPadding(padding, padding, padding, padding);

        ImageView enlargedImage = new ImageView(v.getContext());
        enlargedImage.setImageBitmap(item.image);
        enlargedImage.setScaleType(
                ImageView.ScaleType.FIT_CENTER
        );

        layout.addView(
                enlargedImage,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(v, 300)
                )
        );

        TextView titleView = new TextView(v.getContext());
        titleView.setText("제목: " + item.title);
        titleView.setTextSize(20);
        titleView.setPadding(0, padding, 0, padding / 2);
        layout.addView(titleView);

        TextView contentView = new TextView(v.getContext());
        contentView.setText("내용: " + item.text);
        contentView.setTextSize(16);
        contentView.setPadding(0, 0, 0, padding / 2);
        layout.addView(contentView);

        TextView dateView = new TextView(v.getContext());
        dateView.setText("작성일: " + item.createdDate);
        dateView.setTextSize(14);
        layout.addView(dateView);

        TextView closeView = new TextView(v.getContext());
        closeView.setText("닫기");
        closeView.setTextSize(16);
        closeView.setPadding(0, padding, 0, 0);
        closeView.setOnClickListener(
                view -> dialog.dismiss()
        );
        layout.addView(closeView);

        scrollView.addView(layout);
        dialog.setContentView(scrollView);
        dialog.show();

        Window window = dialog.getWindow();

        if (window != null) {
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    @Override
    public int getItemCount() {
        return imageList.size();
    }

    public static class ImageViewHolder
            extends RecyclerView.ViewHolder {

        ImageView imageView;
        TextView titleView;
        TextView contentView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);

            imageView = itemView.findViewById(
                    R.id.imageViewItem
            );

            if (imageView == null) {
                imageView = itemView.findViewById(
                        android.R.id.icon
                );
            }

            titleView = itemView.findViewById(
                    android.R.id.text1
            );

            contentView = itemView.findViewById(
                    android.R.id.text2
            );
        }
    }
}
