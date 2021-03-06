/*
 * Copyright (c) 2016. Eli Connelly
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.emogoth.android.phone.mimi.fragment;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.ViewStubCompat;
import android.util.Log;
import android.view.View;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.event.GalleryImageTouchEvent;
import com.emogoth.android.phone.mimi.util.BusProvider;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import pl.droidsonroids.gif.GifDrawable;


public class GalleryGifFragment extends GalleryImageBase {
    private static final String LOG_TAG = GalleryGifFragment.class.getSimpleName();
    private AppCompatImageView gifImageView;

    public GalleryGifFragment() {
        doUpdateWhenVisible(true);
    }

    @Override
    public void onViewCreated(View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        inflateLayout(R.layout.gallery_image_gif, new ViewStubCompat.OnInflateListener() {
            @Override
            public void onInflate(ViewStubCompat stub, View view) {
                gifImageView = (AppCompatImageView) view.findViewById(R.id.gif_image);
                if (gifImageView != null) {
                    gifImageView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            BusProvider.getInstance().post(new GalleryImageTouchEvent());
                        }
                    });
                }
            }
        });
    }

    @Override
    public void startAnimation() {
        if (gifImageView.getDrawable() instanceof GifDrawable) {
            ((GifDrawable) gifImageView.getDrawable()).start();
        }
    }

    @Override
    public void stopAnimation() {
        if (gifImageView.getDrawable() instanceof GifDrawable) {
            ((GifDrawable) gifImageView.getDrawable()).stop();
            ((GifDrawable) gifImageView.getDrawable()).seekTo(0);
        }
    }

    @Override
    public void scaleBitmap(final ImageDisplayedListener listener) {
        if (gifImageView.getDrawable() instanceof GifDrawable) {
            GifDrawable drawable = (GifDrawable) gifImageView.getDrawable();
            drawable.pause();

            if (listener != null) {
                final WeakReference<Bitmap> weakBitmap = new WeakReference<>(drawable.seekToPositionAndGet(0));
                listener.onImageDisplayed(null, weakBitmap.get());
            }

            drawable.start();
        }

    }

    @Override
    public void displayImage(final File imageFileName, final boolean isVisible) {
        if (gifImageView == null) {
            return;
        }

        WeakReference<Bitmap> weakBitmap = new WeakReference<>(null);
        showContent();

        try {
            if (gifImageView.getDrawable() == null) {
                initGifDrawable(imageFileName);
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error setting up gif drawable: " + e.getLocalizedMessage());
        }

        if (isVisible) {
            startAnimation();
        } else {
            stopAnimation();
        }

        if (getOnImageDisplayedListener() != null) {
            getOnImageDisplayedListener().onImageDisplayed(this, weakBitmap.get());
        }
    }

    private void initGifDrawable(File imageFileName) throws IOException {
        final GifDrawable gifDrawable = new GifDrawable(imageFileName);
        gifDrawable.setSpeed(1.25F);
        gifImageView.setImageDrawable(gifDrawable);
        gifDrawable.setVisible(true, true);
    }

    @Override
    public String getPageName() {
        return "gallery_gif_image";
    }
}
