<template>
  <div class="error-page">
    <div class="error-content">
      <h1>404</h1>
      <h2>页面不存在</h2>
      <p>抱歉，您访问的页面不存在或已被删除</p>
      <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  background: var(--bg-page);
}

.error-content {
  text-align: center;

  h1 {
    font-size: 120px;
    font-weight: 700;
    color: var(--primary-color);
    margin-bottom: 0;
    line-height: 1;
  }

  h2 {
    font-size: 24px;
    color: var(--text-primary);
    margin: 16px 0;
  }

  p {
    font-size: 14px;
    color: var(--text-secondary);
    margin-bottom: 24px;
  }
}
</style>
