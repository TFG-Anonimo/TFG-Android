package com.example.tennis;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;


public class MainActivity extends AppCompatActivity {

    String TAG = "TAG_";

    InetAddress pcIp;
    EditText ipAddrInput;
    TextView fpsText, connectedToText;
    Button connectButton, dimScreenButton;

    Socket socket;

    AudioRecord recorder;
    private int sampleRate = 44100;
    private int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private int audioFormat = AudioFormat.ENCODING_PCM_8BIT;

    AudioTrack audioTrack;

    public MainActivity() {
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ipAddrInput = findViewById(R.id.et_ip_address);
        connectButton = findViewById(R.id.but_connect);
        connectedToText = findViewById(R.id.tv_connected_to_ip_addr);
        fpsText = findViewById(R.id.tv_fps);
        dimScreenButton = findViewById(R.id.but_dim_screen);

        connectButton.setOnClickListener(v -> {
            try {
                pcIp = InetAddress.getByName(ipAddrInput.getText().toString());
                connectedToText.setText(getString(R.string.connected_to) + " " + pcIp);
                new ConnectTask().execute();
                startStreaming();
            } catch (UnknownHostException e) {
            }
        });

        dimScreenButton.setOnClickListener(v -> {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            if (layout.screenBrightness == 0F)
                layout.screenBrightness = -1;
            else layout.screenBrightness = 0F;
            getWindow().setAttributes(layout);
        });

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET}, 1);

        playSound();
    }


    public void startStreaming() {
        Handler handler = new Handler();
        Runnable runnable = () -> {
            try {
                int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

                Log.d(TAG, String.valueOf(minBufSize));
                byte[] buffer = new byte[minBufSize + 4];

                Log.d(TAG, "Buffer created of size " + minBufSize);

                if (ContextCompat.checkSelfPermission(getApplicationContext(),
                        Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions((Activity) getBaseContext(),
                            new String[]{Manifest.permission.RECORD_AUDIO}, 0);
                }
                recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, channelConfig, audioFormat, minBufSize);

                Log.d(TAG, "Recorder initialized");

                recorder.startRecording();

                /*byte[] sizeBytes = ByteBuffer.allocate(4).putInt(minBufSize + 4).array();
                buffer[0] = sizeBytes[0];
                buffer[1] = sizeBytes[1];
                buffer[2] = sizeBytes[2];
                buffer[3] = sizeBytes[3];*/

                int i = 0;
                while(true) {
                    byte[] sizeBytes = ByteBuffer.allocate(4).putInt(i++).array();
                    buffer[0] = sizeBytes[0];
                    buffer[1] = sizeBytes[1];
                    buffer[2] = sizeBytes[2];
                    buffer[3] = sizeBytes[3];
                    long start = System.currentTimeMillis();
                    //reading data from MIC into buffer
                    int bytesRead = recorder.read(buffer, 4, buffer.length - 4, AudioRecord.READ_BLOCKING);
                    if (bytesRead != 1792) {
                        System.exit(0);
                    }

                    //putting buffer in the packet
                    OutputStream out = socket.getOutputStream();
                    out.write(buffer);
                    out.flush();

                    handler.post(() -> {
                        long now = System.currentTimeMillis() - start;
                        fpsText.setText(getString(R.string.fps) + 1000 / now);
                });
                }
            } catch(UnknownHostException e) {
                Log.e(TAG, "UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "IOException");
            }
        };
        new Thread(runnable).start();
    }

    private byte[] addByteArrays(byte[] v1, byte v2[]) {
        for (int i = 0; i < v1.length; i += 2) {
            byte b0 = v1[i];
            byte b1 = v1[i + 1];

            byte b2 = v2[i];
            byte b3 = v2[i + 1];

            short a = (short) (((b1 & 0xFF) << 8) | (b0 & 0xFF));
            short b = (short) (((b3 & 0xFF) << 8) | (b2 & 0xFF));

            short res = (short) (a + b);

            v1[i] = (byte) (res & 0xff);
            v1[i + 1] = (byte) ((res & 0xff00) >> 8);
        }
        return v1;
    }

    private void playSound() {
        int tones[] = {17600, 17800, 20000, 20200, 20400};

        byte soundSamples[] = genTone(tones[0]);
        for (int i = 1; i < tones.length; ++i)
            soundSamples = addByteArrays(soundSamples, genTone(tones[i]));

        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                44100, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, soundSamples.length,
                AudioTrack.MODE_STATIC);
        audioTrack.write(soundSamples, 0, soundSamples.length);
        audioTrack.setLoopPoints(0, soundSamples.length/4, -1);
        audioTrack.setVolume(1.0f);
        audioTrack.play();
        Log.d(TAG, "Playing sound");
    }

    byte[] genTone(double freqOfTone){
        double sampleR = 44100;
        int numSamples = sampleRate;
        double sample[] = new double[numSamples];

        for (int i = 0; i < numSamples; ++i) {
            sample[i] = Math.sin(2 * Math.PI * i / (sampleR/freqOfTone)); // (-1, 1)
        }

        byte soundSamples[] = new byte[2 * numSamples];
        // Convert to 16 bit pcm sound array
        // Assumes the sample buffer is normalized.
        int idx = 0;
        for (final double dVal : sample) {
            // Scale to maximum amplitude
            final short val = (short) ((dVal * 500));
            // In 16 bit wav PCM, first byte is the low order byte
            soundSamples[idx++] = (byte) (val & 0x00ff);
            soundSamples[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
        return soundSamples;
    }

    private class ConnectTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            while (true) {
                try {
                    socket = new Socket(pcIp, 5555);
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return null;
        }
    }
}