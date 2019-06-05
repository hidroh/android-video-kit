package com.groupme.android.videokit.samples;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.gallery3d.app.TrimVideo;
import com.groupme.android.videokit.AsyncVideoTranscoder;
import com.groupme.android.videokit.VideoTranscoder;
import com.groupme.android.videokit.util.MediaInfo;

import java.io.File;
import java.io.IOException;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_VIDEO = 0;
    private static final int REQUEST_PICK_VIDEO_FOR_TRIM = 1;
    private static final int REQUEST_PICK_VIDEO_FOR_TRIM_ASYNC = 2;
    private static final int REQUEST_TRIM_VIDEO = 3;
    private static final int REQUEST_TRIM_VIDEO_ASYNC = 4;

    private ProgressDialog mProgressDialog;
    private TextView mInputFileSize;
    private TextView mOutputFileSize;
    private TextView mTimeToEncode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button testVideoEncode = (Button) findViewById(R.id.btn_encode_video);
        testVideoEncode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("video/*");
                startActivityForResult(intent, REQUEST_PICK_VIDEO);
            }
        });

        Button testVideoTrim = (Button) findViewById(R.id.btn_trim_video);
        testVideoTrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("video/*");
                startActivityForResult(intent, REQUEST_PICK_VIDEO_FOR_TRIM);
            }
        });

        Button testVideoTrimAsync = (Button) findViewById(R.id.btn_trim_video_async);
        testVideoTrimAsync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("video/*");
                startActivityForResult(intent, REQUEST_PICK_VIDEO_FOR_TRIM_ASYNC);
            }
        });

        mInputFileSize = (TextView) findViewById(R.id.input_file_size);
        mOutputFileSize = (TextView) findViewById(R.id.output_file_size);
        mTimeToEncode = (TextView) findViewById(R.id.time_to_encode);
    }

    private void encodeVideo(final Uri videoUri) throws IOException {
        MediaInfo mediaInfo = new MediaInfo(this, videoUri);

        if (mediaInfo.hasVideoTrack()) {

        }
    }

    private Uri mSrcUri;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_PICK_VIDEO:
                if (resultCode == Activity.RESULT_OK) {
                    try {
                        encodeVideo(data.getData());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case REQUEST_PICK_VIDEO_FOR_TRIM:
            case REQUEST_PICK_VIDEO_FOR_TRIM_ASYNC:
                if (resultCode == Activity.RESULT_OK) {
                    mSrcUri = data.getData();

                    Intent i = new Intent(this, TrimVideo.class);
                    i.putExtra(TrimVideo.EXTRA_MESSAGE, "Welcome to the trimmer!");
                    i.putExtra(TrimVideo.EXTRA_ICON_RES_ID, R.drawable.ic_edit_video);
                    i.putExtra(TrimVideo.EXTRA_MAX_DURATION, 40 * 1000);
                    i.setData(mSrcUri);
                    startActivityForResult(i, requestCode == REQUEST_PICK_VIDEO_FOR_TRIM ? REQUEST_TRIM_VIDEO : REQUEST_TRIM_VIDEO_ASYNC);
                }
                break;
            case REQUEST_TRIM_VIDEO:
            case REQUEST_TRIM_VIDEO_ASYNC:
                if (data != null) {
                    Log.d("TRIM", String.format("Start: %s End: %s", data.getIntExtra(TrimVideo.START_TIME, -1), data.getIntExtra(TrimVideo.END_TIME, -1)));

                    int start = data.getIntExtra(TrimVideo.START_TIME, -1);
                    int end =  data.getIntExtra(TrimVideo.END_TIME, -1);

                    try {
                        MediaInfo info = new MediaInfo(this, mSrcUri);
                        transcode(mSrcUri, info, start, end, requestCode == REQUEST_TRIM_VIDEO_ASYNC);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void transcode(final Uri videoUri, MediaInfo mediaInfo, int start, int end, boolean async) throws IOException {
        final File outputFile = new File(Environment.getExternalStorageDirectory(), "output.mp4");

        if (async) {
            final AsyncVideoTranscoder transcoder = new AsyncVideoTranscoder(this);
            new AsyncTask<Void, Void, VideoTranscoder.Stats>() {
                @Override
                protected VideoTranscoder.Stats doInBackground(Void... voids) {
                    try {
                        return transcoder.extractDecodeEditEncodeMux(videoUri, outputFile);
                    } catch (Exception e) {
                        Log.e("AsyncVideoTranscoder", e + " " + Log.getStackTraceString(e));
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(VideoTranscoder.Stats stats) {
                    super.onPostExecute(stats);
                    if (mProgressDialog.isShowing()) {
                        mProgressDialog.dismiss();
                    }

                    mInputFileSize.setText(String.format("Input file: %sMB", stats.inputFileSize));
                    mOutputFileSize.setText(String.format("Output file: %sMB", stats.outputFileSize));
                    mTimeToEncode.setText(String.format("Time to encode: %ss", stats.timeToTranscode));

                    Button playVideo = (Button) findViewById(R.id.btn_play);
                    playVideo.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(android.content.Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.fromFile(outputFile), "video/*");
                            startActivity(intent);
                        }
                    });
                }
            }.execute();
        } else {
            VideoTranscoder transcoder = new VideoTranscoder.Builder(videoUri, outputFile).build(getApplicationContext());

            transcoder.start(new VideoTranscoder.Listener() {
                @Override
                public void onSuccess(VideoTranscoder.Stats stats) {
                    if (mProgressDialog.isShowing()) {
                        mProgressDialog.dismiss();
                    }

                    mInputFileSize.setText(String.format("Input file: %sMB", stats.inputFileSize));
                    mOutputFileSize.setText(String.format("Output file: %sMB", stats.outputFileSize));
                    mTimeToEncode.setText(String.format("Time to encode: %ss", stats.timeToTranscode));

                    Button playVideo = (Button) findViewById(R.id.btn_play);
                    playVideo.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(android.content.Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.fromFile(outputFile), "video/*");
                            startActivity(intent);
                        }
                    });
                }

                @Override
                public void onFailure() {

                }
            });
        }

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setMessage(String.format("Encoding Video.. (%d secs)", mediaInfo.getDuration()));
        mProgressDialog.show();
    }
}
