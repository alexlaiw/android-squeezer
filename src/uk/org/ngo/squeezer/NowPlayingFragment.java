/*
 * Copyright (c) 2012 Google Inc.  All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.ngo.squeezer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import uk.org.ngo.squeezer.actionbarcompat.ActionBarHelper;
import uk.org.ngo.squeezer.dialogs.ConnectingDialog;
import uk.org.ngo.squeezer.framework.HasUiThread;
import uk.org.ngo.squeezer.framework.SqueezerIconUpdater;
import uk.org.ngo.squeezer.itemlists.SqueezerAlbumListActivity;
import uk.org.ngo.squeezer.itemlists.SqueezerCurrentPlaylistActivity;
import uk.org.ngo.squeezer.itemlists.SqueezerPlayerListActivity;
import uk.org.ngo.squeezer.itemlists.SqueezerSongListActivity;
import uk.org.ngo.squeezer.model.SqueezerAlbum;
import uk.org.ngo.squeezer.model.SqueezerArtist;
import uk.org.ngo.squeezer.model.SqueezerSong;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.SqueezeService;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class NowPlayingFragment extends android.support.v4.app.Fragment implements
        HasUiThread {
    private final String TAG = "NowPlayingFragment";

    private Activity mActivity;
    private ISqueezeService mService = null;

    private final AtomicReference<SqueezerSong> currentSong = new AtomicReference<SqueezerSong>();
    private final AtomicBoolean connectInProgress = new AtomicBoolean(false);

    private ActionBarHelper mActionBarHelper;

    private TextView albumText;
    private TextView artistText;
    private TextView trackText;
    private TextView currentTime;
    private TextView totalTime;
    private MenuItem connectButton;
    private MenuItem disconnectButton;
    private MenuItem poweronButton;
    private MenuItem poweroffButton;
    private MenuItem playersButton;
    private MenuItem playlistButton;
    private MenuItem searchButton;
    private ImageButton playPauseButton;
    private ImageButton nextButton;
    private ImageButton prevButton;
    private ImageView albumArt;
    private SeekBar seekBar;

    private final SqueezerIconUpdater<SqueezerSong> iconUpdater = new SqueezerIconUpdater<SqueezerSong>(
            this);

    // Updating the seekbar
    private boolean updateSeekBar = true;
    private int secondsIn;
    private int secondsTotal;
    private final static int UPDATE_TIME = 1;

    private final Handler uiThreadHandler = new Handler() {
        // Normally I'm lazy and just post Runnables to the uiThreadHandler
        // but time updating is special enough (it happens every second) to
        // take care not to allocate so much memory which forces Dalvik to GC
        // all the time.
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == UPDATE_TIME) {
                updateTimeDisplayTo(secondsIn, secondsTotal);
            }
        }
    };

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            NetworkInfo networkInfo = intent
                    .getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
            if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected()) {
                Log.v(TAG, "Received WIFI connected broadcast");
                if (!isConnected()) {
                    // Requires a serviceStub. Else we'll do this on the service
                    // connection callback.
                    if (mService != null) {
                        Log.v(TAG, "Initiated connect on WIFI connected");
                        startVisibleConnection();
                    }
                }
            }
        }
    };

    private ConnectingDialog connectingDialog = null;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.v(TAG, "ServiceConnection.onServiceConnected()");
            mService = ISqueezeService.Stub.asInterface(binder);
            try {
                NowPlayingFragment.this.onServiceConnected();
            } catch (RemoteException e) {
                Log.e(TAG, "Error in onServiceConnected: " + e);
            }
        }
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        };
    };

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = activity;
        mActionBarHelper = ActionBarHelper.createInstance(mActivity);
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        // Set up a server connection, if it is not present
        if (getConfiguredCliIpPort() == null)
            SettingsActivity.show(mActivity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.now_playing_fragment_full, container, false);

        albumText = (TextView) v.findViewById(R.id.albumname);
        artistText = (TextView) v.findViewById(R.id.artistname);
        trackText = (TextView) v.findViewById(R.id.trackname);
        playPauseButton = (ImageButton) v.findViewById(R.id.pause);
        nextButton = (ImageButton) v.findViewById(R.id.next);
        prevButton = (ImageButton) v.findViewById(R.id.prev);
        albumArt = (ImageView) v.findViewById(R.id.album);
        currentTime = (TextView) v.findViewById(R.id.currenttime);
        totalTime = (TextView) v.findViewById(R.id.totaltime);
        seekBar = (SeekBar) v.findViewById(R.id.seekbar);

        /*
         * TODO: Simplify these following the notes at
         * http://developer.android.com/resources/articles/ui-1.6.html. Maybe.
         * because the TextView resources don't support the android:onClick
         * attribute.
         */
        playPauseButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (mService == null)
                    return;
                try {
                    if (isConnected()) {
                        Log.v(TAG, "Pause...");
                        mService.togglePausePlay();
                    } else {
                        // When we're not connected, the play/pause
                        // button turns into a green connect button.
                        onUserInitiatesConnect();
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Service exception from togglePausePlay(): " + e);
                }
            }
        });

        nextButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (mService == null)
                    return;
                try {
                    mService.nextTrack();
                } catch (RemoteException e) {
                }
            }
        });

        prevButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (mService == null)
                    return;
                try {
                    mService.previousTrack();
                } catch (RemoteException e) {
                }
            }
        });

        artistText.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                SqueezerSong song = getCurrentSong();
                if (song != null) {
                    if (!song.isRemote())
                        SqueezerAlbumListActivity.show(mActivity,
                                new SqueezerArtist(song.getArtist_id(), song.getArtist()));
                }
            }
        });

        albumText.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                SqueezerSong song = getCurrentSong();
                if (song != null) {
                    if (!song.isRemote())
                        SqueezerSongListActivity.show(mActivity,
                                new SqueezerAlbum(song.getAlbum_id(), song.getAlbum()));
                }
            }
        });

        trackText.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                SqueezerSong song = getCurrentSong();
                if (song != null) {
                    if (!song.isRemote())
                        SqueezerSongListActivity.show(mActivity,
                                new SqueezerArtist(song.getArtist_id(), song.getArtist()));
                }
            }
        });

        seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            SqueezerSong seekingSong;

            // Update the time indicator to reflect the dragged thumb position.
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTime.setText(Util.makeTimeString(progress));
                }
            }

            // Disable updates when user drags the thumb.
            public void onStartTrackingTouch(SeekBar s) {
                seekingSong = getCurrentSong();
                updateSeekBar = false;
            }

            // Re-enable updates. If the current song is the same as when
            // we started seeking then jump to the new point in the track,
            // otherwise ignore the seek.
            public void onStopTrackingTouch(SeekBar s) {
                SqueezerSong thisSong = getCurrentSong();

                updateSeekBar = true;

                if (seekingSong == thisSong) {
                    setSecondsElapsed(s.getProgress());
                }
            }
        });

        return v;
    }

    /**
     * Use this to post Runnables to work off thread
     */
    public Handler getUIThreadHandler() {
        return uiThreadHandler;
    }

    // Should only be called the UI thread.
    private void setConnected(boolean connected, boolean postConnect) {
        Log.v(TAG, "setConnected(" + connected + ", " + postConnect + ")");
        if (postConnect) {
            connectInProgress.set(false);
            if (connectingDialog != null) {
                Log.d(TAG, "Dismissing ConnectingDialog");
                connectingDialog.dismiss();
            } else {
                Log.d(TAG, "Got connection failure, but ConnectingDialog wasn't showing");
            }
            connectingDialog = null;
            if (!connected) {
                // TODO: Make this a dialog? Allow the user to correct the
                // server settings here?
                Toast.makeText(mActivity, getText(R.string.connection_failed_text),
                        Toast.LENGTH_LONG)
                        .show();
            }
        }

        // These are all set at the same time, so one check is sufficient
        if (connectButton != null) {
            connectButton.setVisible(!connected);
            disconnectButton.setVisible(connected);
            playersButton.setEnabled(connected);
            playlistButton.setEnabled(connected);
            searchButton.setEnabled(connected);
        }

        nextButton.setEnabled(connected);
        prevButton.setEnabled(connected);
        if (!connected) {
            nextButton.setImageResource(0);
            prevButton.setImageResource(0);
            albumArt.setImageDrawable(null);
            updateSongInfo(null);
            artistText.setText(getText(R.string.disconnected_text));
            currentTime.setText("--:--");
            totalTime.setText("--:--");
            seekBar.setEnabled(false);
            seekBar.setProgress(0);
        } else {
            nextButton.setImageResource(android.R.drawable.ic_media_next);
            prevButton.setImageResource(android.R.drawable.ic_media_previous);
            updateSongInfoFromService();
            seekBar.setEnabled(true);
        }
        updatePlayPauseIcon();
        updateUIForPlayer();
    }

    private void updatePlayPauseIcon() {
        uiThreadHandler.post(new Runnable() {
            public void run() {
                if (!isConnected()) {
                    playPauseButton.setImageResource(R.drawable.presence_online); // green
                                                                                  // circle
                } else if (isPlaying()) {
                    playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                } else {
                    playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                }
            }
        });
    }

    private void updateUIForPlayer() {
        uiThreadHandler.post(new Runnable() {
            public void run() {
                String playerName = getActivePlayerName();
                // XXX: Only do this if full screen
                if (playerName != null && !"".equals(playerName)) {
                    mActivity.setTitle(playerName);
                } else {
                    mActivity.setTitle(getText(R.string.app_name));
                }
                poweronButton.setVisible(canPowerOn());
                poweroffButton.setVisible(canPowerOff());
            }
        });
    }

    protected void onServiceConnected() throws RemoteException {
        Log.v(TAG, "Service bound");
        mService.registerCallback(serviceCallback);
        uiThreadHandler.post(new Runnable() {
            public void run() {
                updateUIFromServiceState();
            }
        });

        // Assume they want to connect...
        if (!isConnected()) {
            startVisibleConnection();
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume...");

        // Start it and have it run forever (until it shuts itself down).
        // This is required so swapping out the activity (and unbinding the
        // service connection in onPause) doesn't cause the service to be
        // killed due to zero refcount.  This is our signal that we want
        // it running in the background.
        mActivity.startService(new Intent(mActivity, SqueezeService.class));

        if (mService != null) {
            uiThreadHandler.post(new Runnable() {
                public void run() {
                    updateUIFromServiceState();
                }
            });
        }

        // XXX: Is this correct? How does the Fragment track WiFi availability?
        if (isAutoConnect())
            mActivity.registerReceiver(broadcastReceiver, new IntentFilter(
                    ConnectivityManager.CONNECTIVITY_ACTION));

        mActivity.bindService(new Intent(mActivity, SqueezeService.class), serviceConnection,
                Context.BIND_AUTO_CREATE);
        Log.d(TAG, "did bindService; serviceStub = " + mService);
    }

    // Should only be called from the UI thread.
    private void updateUIFromServiceState() {
        // Update the UI to reflect connection state. Basically just for
        // the initial display, as changing the prev/next buttons to empty
        // doesn't seem to work in onCreate. (LayoutInflator still running?)
        Log.d(TAG, "updateUIFromServiceState");
        setConnected(isConnected(), false);
    }

    private void updateTimeDisplayTo(int secondsIn, int secondsTotal) {
        if (updateSeekBar) {
            if (seekBar.getMax() != secondsTotal) {
                seekBar.setMax(secondsTotal);
                totalTime.setText(Util.makeTimeString(secondsTotal));
            }
            seekBar.setProgress(secondsIn);
            currentTime.setText(Util.makeTimeString(secondsIn));
        }
    }

    // Should only be called from the UI thread.
    private void updateSongInfoFromService() {
        SqueezerSong song = getCurrentSong();
        updateSongInfo(song);
        updateTimeDisplayTo(getSecondsElapsed(), getSecondsTotal());
        updateAlbumArtIfNeeded(song);
    }

    private void updateSongInfo(SqueezerSong song) {
        if (song != null) {
            artistText.setText(song.getArtist());
            albumText.setText(song.getAlbum());
            trackText.setText(song.getName());
        } else {
            artistText.setText("");
            albumText.setText("");
            trackText.setText("");
        }
    }

    // Should only be called from the UI thread.
    private void updateAlbumArtIfNeeded(SqueezerSong song) {
        Log.v(TAG, "updateAlbumArtIfNeeded");
        if (Util.atomicReferenceUpdated(currentSong, song)) {
            Log.v(TAG, "Calling updateIcon()");
            iconUpdater.updateIcon(albumArt, song, song != null ? song.getArtworkUrl(mService)
                    : null);
        }
    }

    private int getSecondsElapsed() {
        if (mService == null) {
            return 0;
        }
        try {
            return mService.getSecondsElapsed();
        } catch (RemoteException e) {
            Log.e(TAG, "Service exception in getSecondsElapsed(): " + e);
        }
        return 0;
    }

    private int getSecondsTotal() {
        if (mService == null) {
            return 0;
        }
        try {
            return mService.getSecondsTotal();
        } catch (RemoteException e) {
            Log.e(TAG, "Service exception in getSecondsTotal(): " + e);
        }
        return 0;
    }

    private boolean setSecondsElapsed(int seconds) {
        if (mService == null) {
            return false;
        }
        try {
            return mService.setSecondsElapsed(seconds);
        } catch (RemoteException e) {
            Log.e(TAG, "Service exception in setSecondsElapsed(" + seconds + "): " + e);
        }
        return true;
    }

    private SqueezerSong getCurrentSong() {
        if (mService == null) {
            return null;
        }
        try {
            return mService.getCurrentSong();
        } catch (RemoteException e) {
            Log.e(TAG, "Service exception in getCurrentSong(): " + e);
        }
        return null;
    }

    private String getActivePlayerName() {
        if (mService == null) {
            return null;
        }
        try {
            return mService.getActivePlayerName();
        } catch (RemoteException e) {
            Log.e(TAG, "Service exception in getActivePlayerName(): " + e);
        }
        return null;
    }

    private boolean isConnected() {
        if (mService == null) {
            return false;
        }
        try {
            return mService.isConnected();
        } catch (RemoteException e) {
            Log.e(TAG, "Service exception in isConnected(): " + e);
        }
        return false;
    }

    private boolean isPlaying() {
        if (mService == null) {
            return false;
        }
        try {
            return mService.isPlaying();
        } catch (RemoteException e) {
            Log.e(TAG, "Service exception in isPlaying(): " + e);
        }
        return false;
    }

    private boolean canPowerOn() {
        if (mService == null) {
            return false;
        }
        try {
            return mService.canPowerOn();
        } catch (RemoteException e) {
            Log.e(TAG, "Service exception in canPowerOn(): " + e);
        }
        return false;
    }

    private boolean canPowerOff() {
        if (mService == null) {
            return false;
        }
        try {
            return mService.canPowerOff();
        } catch (RemoteException e) {
            Log.e(TAG, "Service exception in canPowerOff(): " + e);
        }
        return false;
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause...");
        if (mService != null) {
            try {
                mService.unregisterCallback(serviceCallback);
                if (serviceConnection != null) {
                    mActivity.unbindService(serviceConnection);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Service exception in onPause(): " + e);
            }
        }
        if (isAutoConnect())
            mActivity.unregisterReceiver(broadcastReceiver);
        super.onPause();
    }

    // @Override
    // public boolean onSearchRequested() {
    // if (isConnected()) {
    // SqueezerSearchActivity.show(mActivity);
    // }
    // return false;
    // }

    /*
     * (non-Javadoc)
     * @see
     * android.support.v4.app.Fragment#onCreateOptionsMenu(android.view.Menu,
     * android.view.MenuInflater)
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater
            inflater) {
        mActionBarHelper.getMenuInflater(inflater).inflate(R.menu.squeezer, menu);
        connectButton = mActionBarHelper.findItem(R.id.menu_item_connect);
        disconnectButton = mActionBarHelper.findItem(R.id.menu_item_disconnect);
        poweronButton = mActionBarHelper.findItem(R.id.menu_item_poweron);
        poweroffButton = mActionBarHelper.findItem(R.id.menu_item_poweroff);
        playersButton = mActionBarHelper.findItem(R.id.menu_item_players);
        playlistButton = mActionBarHelper.findItem(R.id.menu_item_playlist);
        searchButton = mActionBarHelper.findItem(R.id.menu_item_search);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_settings:
                SettingsActivity.show(mActivity);
                return true;
            case R.id.menu_item_search:
                SqueezerSearchActivity.show(mActivity);
                return true;
            case R.id.menu_item_connect:
                onUserInitiatesConnect();
                return true;
            case R.id.menu_item_disconnect:
                try {
                    mService.disconnect();
                } catch (RemoteException e) {
                    Toast.makeText(mActivity, e.toString(),
                            Toast.LENGTH_LONG).show();
                }
                return true;
            case R.id.menu_item_poweron:
                try {
                    mService.powerOn();
                } catch (RemoteException e) {
                    Toast.makeText(mActivity, e.toString(), Toast.LENGTH_LONG).show();
                }
                return true;
            case R.id.menu_item_poweroff:
                try {
                    mService.powerOff();
                } catch (RemoteException e) {
                    Toast.makeText(mActivity, e.toString(),
                            Toast.LENGTH_LONG).show();
                }
                return true;
            case R.id.menu_item_playlist:
                SqueezerCurrentPlaylistActivity.show(mActivity);
                break;
            case R.id.menu_item_players:
                SqueezerPlayerListActivity.show(mActivity);
                return true;
            case R.id.menu_item_about:
                // XXX: Need to correct this (or push it in to the activity).

                // new AboutDialog().show(getSupportFragmentManager(), "AboutDialog");
                return true;
        }
        return false;
    }

    // Returns null if not configured.
    private String getConfiguredCliIpPort() {
        final SharedPreferences preferences = mActivity.getSharedPreferences(Preferences.NAME, 0);
        final String ipPort = preferences.getString(Preferences.KEY_SERVERADDR, null);
        if (ipPort == null || ipPort.length() == 0) {
            return null;
        }
        return ipPort;
    }

    // Returns null if not configured.
    private boolean isAutoConnect() {
        final SharedPreferences preferences = mActivity.getSharedPreferences(Preferences.NAME, 0);
        return preferences.getBoolean(Preferences.KEY_AUTO_CONNECT, true);
    }

    private void onUserInitiatesConnect() {
        // Set up a server connection, if it is not present
        if (getConfiguredCliIpPort() == null) {
            SettingsActivity.show(mActivity);
            return;
        }

        if (mService == null) {
            Log.e(TAG, "serviceStub is null.");
            return;
        }
        startVisibleConnection();
    }

    private void startVisibleConnection() {
        Log.v(TAG, "startVisibleConnection..., connectInProgress: " + connectInProgress.get());
        uiThreadHandler.post(new Runnable() {
            public void run() {
                String ipPort = getConfiguredCliIpPort();
                if (ipPort == null)
                    return;

                if (isAutoConnect()) {
                    WifiManager wifiManager = (WifiManager) mActivity
                            .getSystemService(Context.WIFI_SERVICE);
                    if (!wifiManager.isWifiEnabled()) {
                        // TODO: Understand and fix this commented out block.
                        /*
                         * new EnableWifiDialog()
                         * .show(getSupportFragmentManager(),
                         * "EnableWifiDialog");
                         */
                        return; // We will come back here when Wi-Fi is ready
                    }
                }

                if (connectInProgress.get()) {
                    Log.v(TAG, "Connection is allready in progress, connecting aborted");
                    return;
                }
                connectingDialog = ConnectingDialog.addTo((FragmentActivity) mActivity, ipPort);
                if (connectingDialog != null) {
                    Log.v(TAG, "startConnect, ipPort: " + ipPort);
                    connectInProgress.set(true);
                    try {
                        mService.startConnect(ipPort);
                    } catch (RemoteException e) {
                        Toast.makeText(mActivity, "startConnection error: " + e,
                                Toast.LENGTH_LONG).show();
                    }
                } else {
                    // We couldn't create the connect progress bar. If this
                    // happens because of the android life cycle, then we are
                    // fine, and will get back here shortly, otherwise the user
                    // will have to press the connect button again.
                    Log.v(TAG, "Could not show the connect dialog, connecting aborted");
                }
            }
        });
    }

    public static void show(Context context) {
        final Intent intent = new Intent(context, NowPlayingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    private final IServiceCallback serviceCallback = new IServiceCallback.Stub() {
        public void onConnectionChanged(final boolean isConnected,
                                        final boolean postConnect)
                       throws RemoteException {
            Log.v(TAG, "Connected == " + isConnected + " (postConnect==" + postConnect + ")");
            uiThreadHandler.post(new Runnable() {
                public void run() {
                    setConnected(isConnected, postConnect);
                }
            });
        }

        public void onPlayerChanged(final String playerId,
                                    final String playerName) throws RemoteException {
            Log.v(TAG, "player now " + playerId + ": " + playerName);
            updateUIForPlayer();
        }

        public void onMusicChanged() throws RemoteException {
            uiThreadHandler.post(new Runnable() {
                public void run() {
                    updateSongInfoFromService();
                }
            });
        }

        public void onPlayStatusChanged(boolean newStatus)
                throws RemoteException {
            updatePlayPauseIcon();
        }

        public void onTimeInSongChange(final int secondsIn, final int secondsTotal)
                throws RemoteException {
            NowPlayingFragment.this.secondsIn = secondsIn;
            NowPlayingFragment.this.secondsTotal = secondsTotal;
            uiThreadHandler.sendEmptyMessage(UPDATE_TIME);
        }

        public void onPowerStatusChanged()
                throws RemoteException {
            updateUIForPlayer();
        }

    };

}
