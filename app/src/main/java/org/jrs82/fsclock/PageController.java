package org.jrs82.fsclock;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Paging management: view visibility, swipe gestures, the page indicator and
 *  the long-press callback. The caller provides a shared pages array
 *  (by reference), whose contents ClockController fills in the buildPages phase. */
public class PageController {

    private final ViewGroup pagesContainer;
    private final TextView pageIndicator;
    private final View[] pages;
    private final GestureDetector gesture;
    private final float density;

    private int availablePages = 1;
    private int currentPage = 0;
    private Runnable longPressCallback;
    private PageChangeListener pageChangeListener;
    private TextView topIndicator;
    private String topIndicatorFormat = "%1$d / %2$d";

    public interface PageChangeListener {
        void onPageChanged(int oldPage, int newPage);
    }

    public PageController(Context ctx, ViewGroup pagesContainer,
                          TextView pageIndicator, View[] pages) {
        this.pagesContainer = pagesContainer;
        this.pageIndicator = pageIndicator;
        this.pages = pages;
        this.density = ctx.getResources().getDisplayMetrics().density;
        this.gesture = new GestureDetector(ctx, new SwipeListener());
    }

    @SuppressLint("ClickableViewAccessibility")
    public void start() {
        if (pagesContainer instanceof SwipeInterceptLayout) {
            ((SwipeInterceptLayout) pagesContainer).setSwipeDetector(gesture);
        } else {
            pagesContainer.setOnTouchListener((v, e) -> gesture.onTouchEvent(e));
        }
        showPage(0);
    }

    public void stop() {
        pagesContainer.setOnTouchListener(null);
    }

    public void setLongPressCallback(Runnable r) {
        this.longPressCallback = r;
    }

    public void setPageChangeListener(PageChangeListener l) {
        this.pageChangeListener = l;
    }

    /** Numeric page indicator on the header row (e.g. "2 / 5"). Updated automatically
     *  whenever the page changes or the number of available pages changes. */
    public void setTopIndicator(TextView view, String format) {
        this.topIndicator = view;
        if (format != null) this.topIndicatorFormat = format;
        updatePageIndicator();
    }

    public int getCurrentPage() {
        return currentPage;
    }

    /** Set the number of visible pages (1..pages.length). If the current page
     *  falls outside the new limit, the view moves to the last available
     *  page. */
    public void setAvailablePages(int n) {
        if (n < 1) n = 1;
        if (n > pages.length) n = pages.length;
        this.availablePages = n;
        if (currentPage >= availablePages) {
            showPage(availablePages - 1);
        } else {
            updatePageIndicator();
        }
    }

    public void goTo(int idx) {
        showPage(idx);
    }

    private void showPage(int idx) {
        if (idx < 0) idx = 0;
        if (idx >= availablePages) idx = availablePages - 1;
        int oldPage = currentPage;
        currentPage = idx;
        for (int i = 0; i < pages.length; i++) {
            if (pages[i] != null) pages[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
        }
        updatePageIndicator();
        if (pageChangeListener != null && oldPage != currentPage) {
            pageChangeListener.onPageChanged(oldPage, currentPage);
        }
    }

    private void updatePageIndicator() {
        if (pageIndicator != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < availablePages; i++) {
                sb.append(i == currentPage ? "\u25CF" : "\u25CB");
                if (i < availablePages - 1) sb.append(' ');
            }
            pageIndicator.setText(sb.toString());
        }
        if (topIndicator != null) {
            topIndicator.setText(String.format(java.util.Locale.ROOT,
                    topIndicatorFormat, currentPage + 1, availablePages));
        }
    }

    private int dp(float v) {
        return (int) (v * density + 0.5f);
    }

    private class SwipeListener extends GestureDetector.SimpleOnGestureListener {
        @Override public boolean onDown(MotionEvent e) { return true; }
        @Override public boolean onFling(MotionEvent e1, MotionEvent e2,
                                          float velocityX, float velocityY) {
            if (e1 == null || e2 == null) return false;
            float dx = e2.getX() - e1.getX();
            float dy = e2.getY() - e1.getY();
            if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > dp(60)) {
                if (dx < 0) showPage(currentPage + 1);
                else showPage(currentPage - 1);
                return true;
            }
            return false;
        }
        @Override public void onLongPress(MotionEvent e) {
            if (longPressCallback != null) longPressCallback.run();
        }
    }
}
