import type { Router } from 'vue-router';

import { reactive } from 'vue';

import { ElNotification } from 'element-plus';
import { defineStore } from 'pinia';

import { getSceneAnalysisTaskReport } from '#/api/scene-analysis';

interface PollingTask {
  inFlight: boolean;
  lastCheckedAt: number;
  reportId?: null | number;
  startedAt: number;
  status: string;
  targetCode: string;
  targetName: string;
  taskNo: string;
  timerId?: ReturnType<typeof setTimeout>;
}

interface StartPollingOptions {
  targetCode: string;
  targetName?: null | string;
  taskNo: string;
}

const FIRST_WINDOW_MS = 20_000;
const FIRST_WINDOW_INTERVAL_MS = 10_000;
const NORMAL_INTERVAL_MS = 5000;

export const useReportPollingStore = defineStore('report-polling', () => {
  const tasks = reactive<Record<string, PollingTask>>({});
  let routerRef: Router | undefined;

  function init(router: Router) {
    routerRef = router;
  }

  function start(options: StartPollingOptions) {
    const existing = tasks[options.taskNo];
    if (existing) {
      existing.targetCode = options.targetCode;
      existing.targetName = options.targetName || options.targetCode;
      return;
    }
    tasks[options.taskNo] = {
      inFlight: false,
      lastCheckedAt: 0,
      startedAt: Date.now(),
      status: 'generating_report',
      targetCode: options.targetCode,
      targetName: options.targetName || options.targetCode,
      taskNo: options.taskNo,
    };
    schedule(options.taskNo);
  }

  function stop(taskNo: string) {
    const task = tasks[taskNo];
    if (!task) {
      return;
    }
    if (task.timerId) {
      clearTimeout(task.timerId);
    }
    Reflect.deleteProperty(tasks, taskNo);
  }

  function $reset() {
    for (const taskNo of Object.keys(tasks)) {
      stop(taskNo);
    }
    routerRef = undefined;
  }

  function schedule(taskNo: string) {
    const task = tasks[taskNo];
    if (!task) {
      return;
    }
    const elapsed = Date.now() - task.startedAt;
    const delay =
      elapsed < FIRST_WINDOW_MS ? FIRST_WINDOW_INTERVAL_MS : NORMAL_INTERVAL_MS;
    task.timerId = setTimeout(() => {
      void poll(taskNo);
    }, delay);
  }

  async function poll(taskNo: string) {
    const task = tasks[taskNo];
    if (!task || task.inFlight) {
      return;
    }
    task.inFlight = true;
    try {
      const report = await getSceneAnalysisTaskReport(taskNo);
      task.lastCheckedAt = Date.now();
      task.status = report.status;
      task.reportId = report.reportId;
      if (report.status === 'success') {
        notifySuccess(task, report.reportId);
        stop(taskNo);
        return;
      }
      if (report.status === 'failed') {
        notifyFailed(task, report.errorMessage);
        stop(taskNo);
      }
    } finally {
      const latest = tasks[taskNo];
      if (latest) {
        latest.inFlight = false;
        schedule(taskNo);
      }
    }
  }

  function notifySuccess(task: PollingTask, reportId?: null | number) {
    ElNotification({
      message: task.targetCode,
      onClick: () => {
        if (reportId && routerRef) {
          void routerRef.push({
            name: 'AiSceneReports',
            query: { reportId: String(reportId) },
          });
        }
      },
      title: `${task.targetName} 报告已生成`,
      type: 'success',
    });
  }

  function notifyFailed(task: PollingTask, errorMessage?: null | string) {
    ElNotification({
      message: errorMessage || '请稍后重试',
      title: `${task.targetName} 报告生成失败`,
      type: 'error',
    });
  }

  return {
    $reset,
    init,
    start,
    stop,
    tasks,
  };
});
