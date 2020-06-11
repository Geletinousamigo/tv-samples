/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tv.reference.homescreenchannels

import android.app.Application
import android.content.Context
import android.database.Cursor
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.android.tv.reference.R
import com.android.tv.reference.repository.VideoRepositoryFactory
import com.android.tv.reference.shared.datamodel.Video

/**
 * Helper class that simplifies interactions with the ATV home screen
 */
class HomeScreenChannelHelper(private val previewChannelHelper: PreviewChannelHelper) {

    companion object {
        private const val DEFAULT_CHANNEL_ID = "DEFAULT_CHANNEL_ID"
        private const val DEFAULT_CHANNEL_LINK = "https://atv-reference-app.firebaseapp.com/channels/default"
    }

    // Creates a new default home screen channel and returns its ID
    fun createHomeScreenDefaultChannel(context: Context): Long {
        val logo = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_channel_placeholder) // TODO change to the correct image
        val defaultChannel = PreviewChannel.Builder()
            .setDisplayName(context.getString(R.string.default_home_screen_channel_name))
            .setDescription(context.getString(R.string.default_home_screen_channel_description))
            .setAppLinkIntentUri(Uri.parse(DEFAULT_CHANNEL_LINK))
            .setInternalProviderId(DEFAULT_CHANNEL_ID)
            .setLogo(logo)
            .build()

        return previewChannelHelper.publishDefaultChannel(defaultChannel)
    }

    // Returns videos to include in the default channel
    fun getVideosForDefaultChannel(application: Application, excludedIds: Set<String>, countToAdd: Int): List<Video> {
        val videoRepository = VideoRepositoryFactory.getVideoRepository(application)
        return videoRepository.getAllVideos()
            .filterNot { excludedIds.contains(it.id) } // Exclude videos already in the channel
            .take(countToAdd) // Take only as many as needed to fill the channel
    }

    /**
     * Adds the passed [videos] to the PreviewChannel with [channelId]
     */
    fun addProgramsToChannel(videos: List<Video>, channelId: Long) {
        videos.forEach {
            val program = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE) // TODO set correct type and update unit test
                .setTitle(it.name)
                .setDescription(it.description)
                .setPosterArtUri(Uri.parse(it.thumbnailUri))
                .setIntentUri(Uri.parse(it.uri))
                .setInternalProviderId(it.id)
                .build()

            previewChannelHelper.publishPreviewProgram(program)
        }
    }

    /**
     * Returns the PreviewChannel used as the default if available
     */
    fun getDefaultChannel(): PreviewChannel? {
        return getChannelByInternalProviderId(DEFAULT_CHANNEL_ID)
    }

    /**
     * Returns a PreviewChannel with [internalProviderId] if available
     */
    fun getChannelByInternalProviderId(internalProviderId: String): PreviewChannel? {
        return previewChannelHelper.allChannels.find { internalProviderId == it.internalProviderId }
    }

    /**
     * Returns a [ProgramIdsInChannel] for the passed [channelId]
     */
    fun getProgramIdsInChannel(context: Context, channelId: Long): ProgramIdsInChannel {
        context.contentResolver.query(TvContractCompat.buildPreviewProgramsUriForChannel(channelId), null, null, null, null)?.use {
            return getProgramIdsFromCursor(it)
        }
        return ProgramIdsInChannel()
    }

    /**
     * Returns a [ProgramIdsInChannel] by iterating through the [cursor]
     *
     * The passed [cursor] is _not_ closed
     */
    @VisibleForTesting
    fun getProgramIdsFromCursor(cursor: Cursor): ProgramIdsInChannel {
        val programIdsInChannel = ProgramIdsInChannel()
        val idColumnIndex = cursor.getColumnIndex(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID)
        val browsableColumnIndex = cursor.getColumnIndex(TvContract.PreviewPrograms.COLUMN_BROWSABLE)

        while (cursor.moveToNext()) {
            // Add to the ID list
            programIdsInChannel.addProgramId(cursor.getString(idColumnIndex), cursor.getInt(browsableColumnIndex) == 1)
        }

        return programIdsInChannel
    }
}
