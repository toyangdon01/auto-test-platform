import subprocess
import os

os.chdir(r"C:\Users\Administrator\.openclaw\workspace\products\auto-test-platform\frontend")

# Git checkout
subprocess.run(['git', 'checkout', 'HEAD', '--', 'src/views/tasks/detail.vue'], check=True)
print("Git checkout done")

# Read file
with open(r'src/views/tasks/detail.vue', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add import
old_import = "import { ref, computed, onMounted, onUnmounted } from 'vue'"
new_import = "import { ref, computed, onMounted, onUnmounted } from 'vue'\nimport MetricTimeseriesChart from '@/components/MetricTimeseriesChart.vue'"
content = content.replace(old_import, new_import)

# 2. Find the right place to insert (before "步骤详情弹窗" or "步骤执行详情")
import re
# 查找"步骤执行详情"的 h4 标签
pattern = r'(<!-- 步骤执行详情 -->\s*<div class="page-card mt-20" v-if="taskSteps\.length > 0">)'
match = re.search(pattern, content)

if match:
    insert_pos = match.start()
    component_html = """
    <!-- 指标采集时序图 -->
    <div class="page-card mt-20" v-if="task?.collectEnabled">
      <h4 class="section-title">
        <el-icon><DataLine /></el-icon>
        指标采集趋势
      </h4>
      <MetricTimeseriesChart :task-id="task.id" />
    </div>

"""
    content = content[:insert_pos] + component_html + content[insert_pos:]
    print("Component inserted before '步骤执行详情'")
else:
    print("Pattern not found, trying alternative...")
    # Fallback: add before </template>
    old_end = "</template>"
    new_end = """  <!-- 指标采集时序图 -->
  <div class="page-card mt-20" v-if="task?.collectEnabled">
    <h4 class="section-title">
      <el-icon><DataLine /></el-icon>
      指标采集趋势
    </h4>
    <MetricTimeseriesChart :task-id="task.id" />
  </div>
</template>"""
    content = content.replace(old_end, new_end)
    print("Component added before </template>")

# Write back
with open(r'src/views/tasks/detail.vue', 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")
