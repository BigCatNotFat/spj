package com.example.spj;

/**
 * Callback interface for video recording completion.
 * This ensures that the video file is fully processed before
 * it's added to the gallery or used elsewhere.
 */
public interface VideoCompletionCallback {
    /**
     * Called when video has been successfully encoded and saved.
     *
     * @param path The path to the saved video file
     */
    void onVideoSaved(String path);
}