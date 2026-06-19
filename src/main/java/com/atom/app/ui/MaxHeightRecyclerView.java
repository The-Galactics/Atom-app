package com.atom.app.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.atom.app.R;

/** RecyclerView capped at overlay_transcript_max_height so the panel sheet stays compact. */
public class MaxHeightRecyclerView extends RecyclerView {

    private final int maxHeight;

    public MaxHeightRecyclerView(Context context) {
        this(context, null);
    }

    public MaxHeightRecyclerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MaxHeightRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        maxHeight = getResources().getDimensionPixelSize(R.dimen.overlay_transcript_max_height);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int capped = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
        super.onMeasure(widthSpec, capped);
    }
}
