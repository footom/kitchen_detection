package com.example.detection;

import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.MPPointF;

import java.util.List;

public class MakerView extends MarkerView {

    private TextView tv1, tv2, tv4;
    private ValueFormatter xAxisValueFormatter;

    public MakerView(Context context, ValueFormatter xAxisValueFormatter) {
        super(context, R.layout.makerview);
        this.xAxisValueFormatter = xAxisValueFormatter;

        tv1 = findViewById(R.id.tv1);
        tv2 = findViewById(R.id.tv2);
        tv4 = findViewById(R.id.tv4);
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        Chart chart = getChartView();

        if (chart instanceof LineChart) {
            LineData lineData = ((LineChart) chart).getLineData();
            List<ILineDataSet> dataSetList = lineData.getDataSets();

            for (int i = 0; i < dataSetList.size(); i++) {
                LineDataSet dataSet = (LineDataSet) dataSetList.get(i);
                
                // 使用getEntryForXValue安全取得特定X值的Entry，避免IndexError
                Entry targetEntry = dataSet.getEntryForXValue(e.getX(), Float.NaN);

                if (targetEntry != null) {
                    float y = targetEntry.getY();
                    if (i == 0 && tv1 != null) {
                        tv1.setText(dataSet.getLabel() + ": " + y + " ppm");
                    } else if (i == 1 && tv2 != null) {
                        tv2.setText(dataSet.getLabel() + ": " + y + " ppm");
                    }
                }
            }

            if (tv4 != null && xAxisValueFormatter != null) {
                tv4.setText("時間:" + xAxisValueFormatter.getFormattedValue(e.getX()));
            }
        }
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        // 設定Tooltip偏移量，使其位於點擊位置的正上方中央
        return new MPPointF(-(getWidth() / 2f), -getHeight());
    }
}