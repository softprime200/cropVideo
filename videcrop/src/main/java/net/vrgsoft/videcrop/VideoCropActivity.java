package net.vrgsoft.videcrop;

import android.Manifest;
import android.animation.TimeInterpolator;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;

import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatImageView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.util.Util;

import net.vrgsoft.videcrop.cropview.window.CropVideoView;
import net.vrgsoft.videcrop.ffmpeg.ExecuteBinaryResponseHandler;
import net.vrgsoft.videcrop.ffmpeg.FFmpeg;
import net.vrgsoft.videcrop.ffmpeg.FFtask;
import net.vrgsoft.videcrop.player.VideoPlayer;
import net.vrgsoft.videcrop.view.ProgressView;
import net.vrgsoft.videcrop.view.VideoSliceSeekBarH;

import java.io.File;
import java.util.Formatter;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;


public class VideoCropActivity extends AppCompatActivity implements VideoPlayer.OnProgressUpdateListener, VideoSliceSeekBarH.SeekBarChangeListener {
    private static final String VIDEO_CROP_INPUT_PATH = "VIDEO_CROP_INPUT_PATH";
    private static final String VIDEO_CROP_OUTPUT_PATH = "VIDEO_CROP_OUTPUT_PATH";
    private static final int STORAGE_REQUEST = 100;
    boolean clicked = true;
    final Context context = this;


    private VideoPlayer mVideoPlayer;
    private StringBuilder formatBuilder;
    private Formatter formatter;

    private AppCompatImageView mIvPlay;
    private AppCompatImageView mIvAspectRatio;
    private AppCompatImageView mIvDone;
    private VideoSliceSeekBarH mTmbProgress;
    private CropVideoView mCropVideoView;
    private TextView mTvProgress;
    private TextView mTvDuration;
    private TextView mTvAspectCustom;
    private TextView mTvAspectSquare;
    private TextView mTvAspectPortrait;
    private TextView mTvAspectLandscape;
    private TextView mTvAspect4by3;
    private TextView mTvAspect16by9;
    private TextView mTvCropProgress;
    private TextView text;
    private ImageView square_bg;


    private TextView merge;
    private TextView convert;

    private View mAspectMenu;
    private ProgressView mProgressBar;

    private String inputPath;
    private String outputPath;
    private boolean isVideoPlaying = false;
    private boolean isAspectMenuShown = false;
    private FFtask mFFTask;
    private FFmpeg mFFMpeg;


    String Videopath = "/storage/emulated/0/Download/crop.mp4";
    String Audiopath = "/storage/emulated/0/Download/Idi_song.mp3";
    //    String Audio = String.valueOf(R.raw.music_1);
    //    String Outputpath = "/storage/emulated/0/Download/new.mp4";
    String OutputpathConvert = "/storage/emulated/0/Download/OutputpathConvert.mp4";
    String r="Processing videos";


    public static Intent createIntent(Context context, String inputPath, String outputPath) {
        Intent intent = new Intent(context, VideoCropActivity.class);
        intent.putExtra(VIDEO_CROP_INPUT_PATH, inputPath);
        intent.putExtra(VIDEO_CROP_OUTPUT_PATH, outputPath);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        formatBuilder = new StringBuilder();
        formatter = new Formatter(formatBuilder, Locale.getDefault());

        inputPath = getIntent().getStringExtra(VIDEO_CROP_INPUT_PATH);
        outputPath = getIntent().getStringExtra(VIDEO_CROP_OUTPUT_PATH);

        if (TextUtils.isEmpty(inputPath) || TextUtils.isEmpty(outputPath)) {
            Toast.makeText(this, "input and output paths must be valid and not null", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
        }

        findViews();
        initListeners();

        requestStoragePermission();

        mCropVideoView.setFixedAspectRatio(true);
        mCropVideoView.setAspectRatio(9, 16);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case STORAGE_REQUEST: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initPlayer(inputPath);
                } else {
                    Toast.makeText(this, "You must grant a write storage permission to use this functionality", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_CANCELED);
                    finish();
                }
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isVideoPlaying) {
            mVideoPlayer.play(true);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        mVideoPlayer.play(false);
    }

    @Override
    public void onDestroy() {
        mVideoPlayer.release();
        if (mFFTask != null && !mFFTask.isProcessCompleted()) {
            mFFTask.sendQuitSignal();
        }
        if (mFFMpeg != null) {
            mFFMpeg.deleteFFmpegBin();
        }
        super.onDestroy();
    }

    @Override
    public void onFirstTimeUpdate(long duration, long currentPosition) {
        mTmbProgress.setSeekBarChangeListener(this);
        mTmbProgress.setMaxValue(duration);
        mTmbProgress.setLeftProgress(0);
        mTmbProgress.setRightProgress(duration);
        mTmbProgress.setProgressMinDiff(0);
    }

    @Override
    public void onProgressUpdate(long currentPosition, long duration, long bufferedPosition) {
        mTmbProgress.videoPlayingProgress(currentPosition);
        if (!mVideoPlayer.isPlaying() || currentPosition >= mTmbProgress.getRightProgress()) {
            if (mVideoPlayer.isPlaying()) {
                playPause();
            }
        }

        mTmbProgress.setSliceBlocked(false);
        mTmbProgress.removeVideoStatusThumb();

//        mTmbProgress.setPosition(currentPosition);
//        mTmbProgress.setBufferedPosition(bufferedPosition);
//        mTmbProgress.setDuration(duration);
    }

    private void findViews() {
        mCropVideoView = findViewById(R.id.cropVideoView);
        mIvPlay = findViewById(R.id.ivPlay);
        mIvAspectRatio = findViewById(R.id.ivAspectRatio);
        mIvDone = findViewById(R.id.ivDone);
        mTvProgress = findViewById(R.id.tvProgress);
        mTvDuration = findViewById(R.id.tvDuration);
        mTmbProgress = findViewById(R.id.tmbProgress);
        mAspectMenu = findViewById(R.id.aspectMenu);
//        mTvAspectCustom = findViewById(R.id.tvAspectCustom);
//        mTvAspectSquare = findViewById(R.id.tvAspectSquare);
//        mTvAspectPortrait = findViewById(R.id.tvAspectPortrait);
//        mTvAspectLandscape = findViewById(R.id.tvAspectLandscape);
//        mTvAspect4by3 = findViewById(R.id.tvAspect4by3);
//        mTvAspect16by9 = findViewById(R.id.tvAspect16by9);
        mProgressBar = findViewById(R.id.pbCropProgress);
        mTvCropProgress = findViewById(R.id.tvCropProgress);
        text = findViewById(R.id.text);
        square_bg=findViewById(R.id.squaree);

//        merge = findViewById(R.id.merge);
//        convert = findViewById(R.id.convert);

    }

    private void initListeners() {
        mIvPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playPause();
            }
        });


        mIvAspectRatio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleMenuVisibility();
            }
        });
//        mTvAspectCustom.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                mCropVideoView.setFixedAspectRatio(false);
//                handleMenuVisibility();
//            }
//        });
//        mTvAspectSquare.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                mCropVideoView.setFixedAspectRatio(true);
//                mCropVideoView.setAspectRatio(10, 10);
//                handleMenuVisibility();
//            }
//        });
//        mTvAspectPortrait.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                mCropVideoView.setFixedAspectRatio(true);
//                mCropVideoView.setAspectRatio(8, 16);
//                handleMenuVisibility();
//            }
//        });
//        mTvAspectLandscape.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                mCropVideoView.setFixedAspectRatio(true);
//                mCropVideoView.setAspectRatio(16, 8);
//                handleMenuVisibility();
//            }
//        });
//        mTvAspect4by3.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                mCropVideoView.setFixedAspectRatio(true);
//                mCropVideoView.setAspectRatio(4, 3);
//                handleMenuVisibility();
//            }
//        });
//        mTvAspect16by9.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                mCropVideoView.setFixedAspectRatio(true);
//                mCropVideoView.setAspectRatio(16, 9);
//                handleMenuVisibility();
//            }
//        });
//
//        merge.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//
////                handleMergeStart();
//                handleMenuVisibility();
//            }
//        });

//        convert.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
////               handleConvertStart();
//                handleMenuVisibility();
//            }
//        });


        mIvDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

//                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
//                int videoWidth = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
//                int videoHeight = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
//                int rotationDegrees = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
//                float AspectRatio= (float) videoHeight/videoWidth;

                if (clicked) {
                    Resources res = context.getResources();
                    int newColor = res.getColor(android.R.color.darker_gray);
                    mIvDone.setColorFilter(newColor, Mode.SRC_ATOP);
                    mIvPlay.setColorFilter(newColor, Mode.SRC_ATOP);
                    handleCropStart();
//                    handleConvertStart();
                }
//                    if ((AspectRatio>1.75 && AspectRatio <1.79)){
//
//                        Toast.makeText(VideoCropActivity.this,"same Size",Toast.LENGTH_SHORT).show();
//                    }


            }
        });
    }

    private void playPause() {
        isVideoPlaying = !mVideoPlayer.isPlaying();
        if (mVideoPlayer.isPlaying()) {
            mVideoPlayer.play(!mVideoPlayer.isPlaying());
            mTmbProgress.setSliceBlocked(false);
            mTmbProgress.removeVideoStatusThumb();
            mIvPlay.setImageResource(R.drawable.ic_play);
            return;
        }
        mVideoPlayer.seekTo(mTmbProgress.getLeftProgress());
        mVideoPlayer.play(!mVideoPlayer.isPlaying());
        mTmbProgress.videoPlayingProgress(mTmbProgress.getLeftProgress());
        mIvPlay.setImageResource(R.drawable.ic_pause);
    }

    private void initPlayer(String uri) {
        if (!new File(uri).exists()) {
            Toast.makeText(this, "File doesn't exists", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        mVideoPlayer = new VideoPlayer(this);
        mCropVideoView.setPlayer(mVideoPlayer.getPlayer());
        mVideoPlayer.initMediaSource(this, uri);
        mVideoPlayer.setUpdateListener(this);




        fetchVideoInfo(uri);
    }

//    private void fetchVideoInfo(String uri) {
//
//        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
//        retriever.setDataSource(new File(uri).getAbsolutePath());
//
//
//
//
//
//            // new
//            String time=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
//            long timeInMillisec=Long.parseLong(time);
//            Log.e("Publish Adpater", "Time  "+ timeInMillisec);
//            if (timeInMillisec>600000){
//
//                Toast.makeText(getApplicationContext()," Select a video less than 10 mins ",Toast.LENGTH_SHORT).show();
//                this.onBackPressed();
//            }
////
//
//        int videoWidth = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
//        int videoHeight = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
//        int rotationDegrees = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
//
//        mCropVideoView.initBounds(videoWidth, videoHeight, rotationDegrees);
//    }

    private void fetchVideoInfo(String uri) {
//        Bitmap bmp = null;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();

        // new
//        bmp = retriever.getFrameAtTime();
//        int videoHeight1 = bmp.getHeight();
//        int videoWidth1 = bmp.getWidth();

        retriever.setDataSource(new File(uri).getAbsolutePath());
        int videoWidth = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        int videoHeight = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        int rotationDegrees = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));


        //new
        float AspectRatio= (float) videoHeight/videoWidth;

//        int AspectRatio1=videoHeight1/videoWidth1;


        // new
        String time=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        long timeInMillisec=Long.parseLong(time);
        Log.e("Publish Adpater", "Time  "+ timeInMillisec);
        if (timeInMillisec>600000){

            Toast.makeText(getApplicationContext()," Select a video less than 10 mins ",Toast.LENGTH_SHORT).show();
            this.onBackPressed();

        }

//        Toast.makeText(VideoCropActivity.this,"same Size= " + AspectRatio ,Toast.LENGTH_SHORT).show();
//
//        if (AspectRatio>1.75 && AspectRatio <1.79){
//
//            Toast.makeText(VideoCropActivity.this,"same Size",Toast.LENGTH_SHORT).show();
//
//        }







        mCropVideoView.initBounds(videoWidth, videoHeight, rotationDegrees);
    }







    private void handleMenuVisibility() {
        isAspectMenuShown = !isAspectMenuShown;
        TimeInterpolator interpolator;
        if (isAspectMenuShown) {
            interpolator = new DecelerateInterpolator();
        } else {
            interpolator = new AccelerateInterpolator();
        }
        mAspectMenu.animate()
                .translationY(isAspectMenuShown ? 0 : Resources.getSystem().getDisplayMetrics().density * 400)
                .alpha(isAspectMenuShown ? 1 : 0)
                .setInterpolator(interpolator)
                .start();
    }

    private void requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_REQUEST);
        } else {
            initPlayer(inputPath);
        }
    }


//
//    @SuppressLint("DefaultLocale")
//    private void handleSentStart() {
//
//
//        if (mFFMpeg.isSupported()) {
//            String[] cmd = {"-i",
//                    inputPath,
//                    "-filter:v",
//                    "scale=320:-1",
//                    "-c:a",
//                    "copy",
//                    outputPath
//
//
//            };
//
////            mFFTask = mFFMpeg.execute(cmd, new ExecuteBinaryResponseHandler() {
////                @Override
////                public void onSuccess(String message) {
////                    setResult(RESULT_OK);
////                    Log.e("onSuccess", message);
////                    finish();
////                }
//
////                @Override
////                public void onProgress(String message) {
////                    Log.e("onProgress", message);
////                }
//
////                @Override
////                public void onFailure(String message) {
////                    Toast.makeText(VideoCropActivity.this, "Failed to crop!", Toast.LENGTH_SHORT).show();
////
//////
////
////                }
////
////                @Override
////                public void onProgressPercent(float percent) {
////                    mProgressBar.setProgress((int) percent);
////                    mTvCropProgress.setText((int) percent + "%");
////                    text.setText("Processing video");
////                }
//
////                @Override
////                public void onStart() {
////                    mIvDone.setEnabled(false);
////                    mIvPlay.setEnabled(false);
////                    mProgressBar.setVisibility(View.VISIBLE);
////                    mProgressBar.setProgress(0);
////                    mTvCropProgress.setVisibility(View.VISIBLE);
////                    mTvCropProgress.setText("0%");
////                    mTvCropProgress.setTextColor(Color.WHITE);
////                    text.setText("Processing video");
////                    text.setTextColor(Color.WHITE);
////                    square_bg.setVisibility(View.VISIBLE);
////
////
////                }
////
////                @Override
////                public void onFinish() {
////                    mIvDone.setEnabled(true);
////                    mIvPlay.setEnabled(true);
////                    mProgressBar.setVisibility(View.INVISIBLE);
////                    mProgressBar.setProgress(0);
////                    mTvCropProgress.setVisibility(View.INVISIBLE);
////                    text.setVisibility(View.INVISIBLE);
////                    mTvCropProgress.setText("0%");
//        }
//    }

    @SuppressLint("DefaultLocale")
    private void handleConvertStart() {

        mFFMpeg = FFmpeg.getInstance(this);
        if (mFFMpeg.isSupported()) {
            String[] cmd = {

                    "-i",
                    inputPath,
                    "-filter:v",
                    "scale=1080:540",
                    "-c:a",
                    "copy",
                    OutputpathConvert

//                    "-i",
//                    inputPath,
//                    "-vf",
//                    "scale=1080:540",
//                    outputPath

            };

            mFFTask = mFFMpeg.execute(cmd, new ExecuteBinaryResponseHandler() {
                @Override
                public void onSuccess(String message) {
                    setResult(RESULT_OK);
                    Log.e("onSuccess", message);
                    finish();
                }

                @Override
                public void onProgress(String message) {
                    Log.e("onProgress", message);
                }

                @Override
                public void onFailure(String message) {
                    Toast.makeText(VideoCropActivity.this, "Failed to crop!", Toast.LENGTH_SHORT).show();
                    Log.e("onFailure", message);
                }

                @Override
                public void onProgressPercent(float percent) {
                    mProgressBar.setProgress((int) percent);
                    mTvCropProgress.setText((int) percent + "%");
                }

                @Override
                public void onStart() {
                    mIvDone.setEnabled(false);
                    mIvPlay.setEnabled(false);
                    mProgressBar.setVisibility(View.VISIBLE);
                    mProgressBar.setProgress(0);
                    mTvCropProgress.setVisibility(View.VISIBLE);
                    mTvCropProgress.setText("0%");
                    mTvCropProgress.setTextColor(Color.BLUE);
                }

                @Override
                public void onFinish() {
                    mIvDone.setEnabled(true);
                    mIvPlay.setEnabled(true);
                    mProgressBar.setVisibility(View.INVISIBLE);
                    mProgressBar.setProgress(0);
                    mTvCropProgress.setVisibility(View.INVISIBLE);
                    mTvCropProgress.setText("0%");
                    Toast.makeText(VideoCropActivity.this, "Finished converting", Toast.LENGTH_SHORT).show();

                }
            },  10);
        }
    }

    /////
    @SuppressLint("DefaultLocale")
    private void handleCropStart() {
        Rect cropRect = mCropVideoView.getCropRect();
        long startCrop = mTmbProgress.getLeftProgress();
        long durationCrop = mTmbProgress.getRightProgress() - mTmbProgress.getLeftProgress();
        String start = Util.getStringForTime(formatBuilder, formatter, startCrop);
        String duration = Util.getStringForTime(formatBuilder, formatter, durationCrop);
        start += "." + startCrop % 1000;
        duration += "." + durationCrop % 1000;

        mFFMpeg = FFmpeg.getInstance(this);
        if (mFFMpeg.isSupported()) {
            String crop = String.format("crop=%d:%d:%d:%d:exact=0", cropRect.right, cropRect.bottom, cropRect.left, cropRect.top);
            String[] cmd = {
                    "-y",
                    "-ss",
                    start,
                    "-i",
                    inputPath,
                    "-t",
                    duration,
                    "-vf",
                    crop,
                    outputPath

//                    "-i",
//                    inputPath,
//                    "-filter:v",
//                    "scale=1080:540",
//                    "-c:a",
//                    "copy",
//                    OutputpathConvert

            };

            mFFTask = mFFMpeg.execute(cmd, new ExecuteBinaryResponseHandler() {
                @Override
                public void onSuccess(String message) {
                    setResult(RESULT_OK);
                    Log.e("onSuccess", message);
                    finish();
                }

                @Override
                public void onProgress(String message) {
                    Log.e("onProgress", message);
                }

                @Override
                public void onFailure(String message) {
                    Toast.makeText(VideoCropActivity.this, "Failed to crop!", Toast.LENGTH_SHORT).show();

//

                }

                @Override
                public void onProgressPercent(float percent) {
                    mProgressBar.setProgress((int) percent);
                    mTvCropProgress.setText((int) percent + "%");
                    text.setVisibility(View.VISIBLE);
                    text.setText(r);
                }

                @Override
                public void onStart() {
                    mIvDone.setEnabled(false);
                    mIvPlay.setEnabled(false);
                    mProgressBar.setVisibility(View.VISIBLE);
                    mProgressBar.setProgress(0);
                    mTvCropProgress.setVisibility(View.VISIBLE);
                    mTvCropProgress.setText("0%");
                    mTvCropProgress.setTextColor(Color.WHITE);
                    text.setVisibility(View.VISIBLE);
                    text.setText(r);
                    text.setTextColor(Color.WHITE);
                    square_bg.setVisibility(View.VISIBLE);



                }

                @Override
                public void onFinish() {
                    mIvDone.setEnabled(true);
                    mIvPlay.setEnabled(true);
                    mProgressBar.setVisibility(View.INVISIBLE);
                    mProgressBar.setProgress(0);
                    mTvCropProgress.setVisibility(View.INVISIBLE);
                    text.setVisibility(View.INVISIBLE);
                    square_bg.setVisibility(View.INVISIBLE);


                    mTvCropProgress.setText("0%");
                    Toast.makeText(VideoCropActivity.this, "Finished crop", Toast.LENGTH_SHORT).show();
                }
            }, durationCrop * 1.0f / 1000);
        }
    }

    @Override
    public void seekBarValueChanged(long leftThumb, long rightThumb) {
        if (mTmbProgress.getSelectedThumb() == 1) {
            mVideoPlayer.seekTo(leftThumb);
        }

        mTvDuration.setText(Util.getStringForTime(formatBuilder, formatter, rightThumb));
        mTvProgress.setText(Util.getStringForTime(formatBuilder, formatter, leftThumb));
    }

}
