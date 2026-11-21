import { useState, useEffect } from 'react';
import ReactECharts from 'echarts-for-react';
import { fetchHomeTrend, HomeTrend } from '../api';

function formatTrendDateLabel(value: string): string {
  if (!value) return '';
  const [year = '', month = '', day = ''] = value.split('-');
  if (!year || !month || !day) return value;
  return `${month}-${day}`;
}

export default function TrendChart({ days = 30 }: { days?: number }) {
  const [trendData, setTrendData] = useState<HomeTrend[]>([]);

  useEffect(() => {
    let active = true;
    setTrendData([]);
    fetchHomeTrend(days)
      .then((data) => {
        if (active && data && data.length > 0) {
          setTrendData(data);
        }
      })
      .catch(console.error);
    return () => { active = false; };
  }, [days]);

  if (trendData.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center text-gray-400 text-sm">
        暂无趋势数据
      </div>
    );
  }

  const axisLabelStep = trendData.length <= 10 ? 0 : Math.max(1, Math.ceil(trendData.length / 8) - 1);

  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e5e7eb',
      borderWidth: 1,
      textStyle: {
        color: '#4a3b32',
        fontSize: 12,
      },
      formatter: (params: Array<{ dataIndex?: number; seriesName?: string; value?: number; marker?: string }>) => {
        const index = params?.[0]?.dataIndex ?? 0;
        const current = trendData[index];
        if (!current) return '';

        const lines = params.map((item) => {
          const value = Number(item.value ?? 0);
          return `${item.marker || ''}${item.seriesName}：${value}`;
        });

        return [
          `<div style="font-weight:600;margin-bottom:6px;color:#162033;">${current.date}</div>`,
          ...lines.map((line) => `<div style="margin:2px 0;">${line}</div>`),
        ].join('');
      },
    },
    legend: {
      data: ['量表评估', '风险检测', '低风险', '中风险', '高风险'],
      top: 0,
      right: 0,
      textStyle: {
        color: '#516276',
        fontSize: 11,
      },
      itemWidth: 12,
      itemHeight: 8,
      itemGap: 16,
    },
    grid: {
      left: 44,
      right: 16,
      bottom: 46,
      top: 36,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      boundaryGap: true,
      data: trendData.map((item) => item.date),
      axisLine: {
        lineStyle: {
          color: '#e5e7eb',
        },
      },
      axisLabel: {
        color: '#6B7B8F',
        fontSize: 10,
        rotate: 36,
        margin: 10,
        hideOverlap: true,
        formatter: (value: string) => formatTrendDateLabel(value),
        interval: axisLabelStep,
      },
      axisTick: {
        show: false,
      },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: {
        show: false,
      },
      axisLabel: {
        color: '#6B7B8F',
        fontSize: 10,
      },
      splitLine: {
        lineStyle: {
          color: '#f2e8e0',
          type: 'dashed',
        },
      },
    },
    series: [
      {
        name: '量表评估',
        type: 'line',
        smooth: true,
        data: trendData.map((item) => item.scaleCount),
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: {
          color: '#f97316',
          width: 2,
        },
        itemStyle: {
          color: '#f97316',
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(249, 115, 22, 0.2)' },
              { offset: 1, color: 'rgba(249, 115, 22, 0)' },
            ],
          },
        },
      },
      {
        name: '风险检测',
        type: 'line',
        smooth: true,
        data: trendData.map((item) => item.detectionCount),
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: {
          color: '#3b82f6',
          width: 2,
        },
        itemStyle: {
          color: '#3b82f6',
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(59, 130, 246, 0.15)' },
              { offset: 1, color: 'rgba(59, 130, 246, 0)' },
            ],
          },
        },
      },
      {
        name: '低风险',
        type: 'bar',
        stack: 'risk',
        data: trendData.map((item) => item.riskLow),
        itemStyle: {
          color: '#22c55e',
        },
        barMaxWidth: 16,
      },
      {
        name: '中风险',
        type: 'bar',
        stack: 'risk',
        data: trendData.map((item) => item.riskMedium),
        itemStyle: {
          color: '#eab308',
        },
      },
      {
        name: '高风险',
        type: 'bar',
        stack: 'risk',
        data: trendData.map((item) => item.riskHigh),
        itemStyle: {
          color: '#ef4444',
        },
      },
    ],
  };

  return (
    <div className="flex-1 w-full min-h-[220px]">
      <ReactECharts
        option={option}
        style={{ height: '100%', width: '100%', minHeight: '220px' }}
        opts={{ renderer: 'canvas' }}
      />
    </div>
  );
}


