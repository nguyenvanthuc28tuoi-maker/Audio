package com.example.audio

import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        mediaSession = MediaSession.Builder(
            this,
            player
        )
            .setCallback(
                object : MediaSession.Callback {

                    override fun onConnect(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo
                    ): MediaSession.ConnectionResult {

                        val commands =
                            MediaSession.ConnectionResult
                                .DEFAULT_PLAYER_COMMANDS
                                .buildUpon()
                                .add(
                                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
                                )
                                .add(
                                    Player.COMMAND_SEEK_BACK
                                )
                                .add(
                                    Player.COMMAND_SEEK_FORWARD
                                )
                                .add(
                                    Player.COMMAND_PLAY_PAUSE
                                )
                                .add(
                                    Player.COMMAND_GET_TIMELINE
                                )
                                .add(
                                    Player.COMMAND_GET_METADATA
                                )
                                .build()

                        return MediaSession.ConnectionResult
                            .AcceptedResultBuilder(session)
                            .setAvailablePlayerCommands(commands)
                            .build()
                    }
                }
            )
            .build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Dừng hoàn toàn việc phát khi vuốt app khỏi Recent Apps
        player.stop()
        player.clearMediaItems()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession.release()
        player.release()

        super.onDestroy()
    }
}