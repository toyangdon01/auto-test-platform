<template>
  <div class="page-card">
    <div class="page-header">
      <el-page-header @back="$router.back()">
        <template #content>
          <span class="title">报告详情</span>
        </template>
      </el-page-header>
      
      <div class="header-actions">
        <el-button type="primary" @click="handleDownload">下载 PDF</el-button>
        <el-button @click="handlePrint">打印</el-button>
      </div>
    </div>

    <div class="report-content">
      <h2 class="report-title">CPU压力测试报告</h2>
      <p class="report-meta">生成时间：2026-03-09 11:00 | 执行服务器：Server-01, Server-02</p>

      <el-divider />

      <h3>一、测试概述</h3>
      <p>本次测试对服务器进行CPU压力测试，持续时间为60秒，并发线程数为8。</p>

      <h3>二、测试结果</h3>
      <el-table :data="results" stripe>
        <el-table-column prop="name" label="测试项" />
        <el-table-column prop="value" label="结果" />
        <el-table-column prop="status" label="判定">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PASS' ? 'success' : 'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <h3 class="mt-20">三、结论与建议</h3>
      <p>测试通过。服务器CPU性能达标，建议继续监控长期运行稳定性。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const results = ref([
  { name: 'CPU使用率峰值', value: '98.5%', status: 'PASS' },
  { name: 'CPU温度峰值', value: '75°C', status: 'PASS' },
  { name: '系统响应时间', value: '15ms', status: 'PASS' },
])

function handleDownload() {
  console.log('download')
}

function handlePrint() {
  window.print()
}
</script>

<style lang="scss" scoped>
.report-content {
  padding: 20px;
  background: #fff;
}

.report-title {
  font-size: 24px;
  text-align: center;
  margin-bottom: 10px;
}

.report-meta {
  text-align: center;
  color: var(--text-secondary);
  margin-bottom: 20px;
}

h3 {
  margin: 20px 0 10px;
  font-size: 16px;
}
</style>
