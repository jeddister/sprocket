// Copyright (c) 2015 University of Szeged.
// Copyright (c) 2015 The Chromium Authors.
// All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package hu.uszeged.sprocket;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.content.browser.ActivityContentVideoViewEmbedder;
import org.chromium.content.browser.ContentVideoViewEmbedder;
import org.chromium.content.browser.ContentViewClient;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.ContentViewRenderView;
import org.chromium.ui.base.WindowAndroid;

/**
 * Container and generator of SprocketWindow.
 */
//@JNINamespace("content")
public class SprocketManager extends FrameLayout {

    public static final String DEFAULT_SPROCKET_URL = "http://www.google.com";
    private static boolean sStartup = true;
    private WindowAndroid mWindow;
    private SprocketWindow mActiveSprocketWindow;

    private String mStartupUrl = DEFAULT_SPROCKET_URL;

    // The target for all content rendering.
    private ContentViewRenderView mContentViewRenderView;
    private ContentViewClient mContentViewClient;

    /**
     * Constructor for inflating via XML.
     */
    public SprocketManager(final Context context, AttributeSet attrs) {
        super(context, attrs);
        nativeInit(this);
        mContentViewClient = new ContentViewClient() {
            @Override
            public ContentVideoViewEmbedder getContentVideoViewEmbedder() {
                return new ActivityContentVideoViewEmbedder((Activity) context) {
                    @Override
                    public void enterFullscreenVideo(View view) {
                        super.enterFullscreenVideo(view);
                        setOverlayVideoMode(true);
                    }

                    @Override
                    public void exitFullscreenVideo() {
                        super.exitFullscreenVideo();
                        setOverlayVideoMode(false);
                    }
                };
            }
        };
    }

    /**
     * @param window The window used to generate all Sprockets.
     */
    public void setWindow(WindowAndroid window) {
        setWindow(window, true);
    }

    /**
     * @param window The window used to generate all Sprockets.
     * @param initialLoadingNeeded Whether initial loading is needed or not.
     */
    @VisibleForTesting
    public void setWindow(WindowAndroid window, final boolean initialLoadingNeeded) {
        assert window != null;
        mWindow = window;
        mContentViewRenderView = new ContentViewRenderView(getContext()) {
            @Override
            protected void onReadyToRender() {
                if (sStartup) {
                    if (initialLoadingNeeded) mActiveSprocketWindow.loadUrl(mStartupUrl);
                    sStartup = false;
                }
            }
        };
        mContentViewRenderView.onNativeLibraryLoaded(window);
    }

    /**
     * @return The window used to generate all Sprockets.
     */
    public WindowAndroid getWindow() {
        return mWindow;
    }

    /**
     * Get the ContentViewRenderView.
     */
    public ContentViewRenderView getContentViewRenderView() {
        return mContentViewRenderView;
    }

    /**
     * Sets the startup URL for new SprocketWindows.
     */
    public void setStartupUrl(String url) {
        mStartupUrl = url;
    }

    /**
     * @return The currently visible SprocketWindow or null if one is not showing.
     */
    public SprocketWindow getActiveSprocketWindow() {
        return mActiveSprocketWindow;
    }

    /**
     * Creates a new SprocketWindow pointing to the specified URL.
     * @param url The URL the SprocketWindow should load upon creation.
     */
    public void launchSprocketWindow(String url) {
        ThreadUtils.assertOnUiThread();
        SprocketWindow previousSprocketWindow = mActiveSprocketWindow;
        nativeLaunchSprocketWindow(url);
        if (previousSprocketWindow != null) previousSprocketWindow.close();
    }

    /**
     * Enter or leave overlay video mode.
     * @param enabled Whether overlay mode is enabled.
     */
    public void setOverlayVideoMode(boolean enabled) {
        if (mContentViewRenderView == null) return;
        mContentViewRenderView.setOverlayVideoMode(enabled);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private Object createSprocketWindow(long nativeSprocketWindowPtr) {
        assert mContentViewRenderView != null;
        LayoutInflater inflater =
                (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        SprocketWindow sprocketWindow = (SprocketWindow) inflater.inflate(R.layout.sprocket_view, null);
        sprocketWindow.initialize(nativeSprocketWindowPtr, mWindow, mContentViewClient);

        // TODO(tedchoc): Allow switching back to these inactive SprocketWindow.
        if (mActiveSprocketWindow != null) removeSprocketWindow(mActiveSprocketWindow);

        showSprocketWindow(sprocketWindow);
        return sprocketWindow;
    }

    private void showSprocketWindow(SprocketWindow sprocketWindow) {
        sprocketWindow.setContentViewRenderView(mContentViewRenderView);
        addView(sprocketWindow, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        mActiveSprocketWindow = sprocketWindow;
        ContentViewCore contentViewCore = mActiveSprocketWindow.getContentViewCore();
        if (contentViewCore != null) {
            mContentViewRenderView.setCurrentContentViewCore(contentViewCore);
            contentViewCore.onShow();
        }
    }

    @CalledByNative
    private void removeSprocketWindow(SprocketWindow sprocketWindow) {
        if (sprocketWindow == mActiveSprocketWindow) mActiveSprocketWindow = null;
        if (sprocketWindow.getParent() == null) return;
        ContentViewCore contentViewCore = sprocketWindow.getContentViewCore();
        if (contentViewCore != null) contentViewCore.onHide();
        sprocketWindow.setContentViewRenderView(null);
        removeView(sprocketWindow);
    }

    private static native void nativeInit(Object sprocketManagerInstance);
    private static native void nativeLaunchSprocketWindow(String url);
}
