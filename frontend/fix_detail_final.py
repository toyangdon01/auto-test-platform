import subprocess
import os

os.chdir(r"C:\Users\Administrator\.openclaw\workspace\products\auto-test-platform\frontend")

# 1. Git checkout
subprocess.run(['git', 'checkout', 'HEAD', '--', 'src/views/tasks/detail.vue'], check=True)
print("✓ Git checkout done")

# 2. Read and rewrite as UTF-8
with open(r'src/views/tasks/detail.vue', 'r', encoding='utf-8') as f:
    content = f.read()

# 3. Add import
old_import = "import { ref, computed, onMounted, onUnmounted } from 'vue'"
new_import = "import { ref, computed, onMounted, onUnmounted } from 'vue'\nimport MetricTimeseriesChart from '@/components/MetricTimeseriesChart.vue'"
content = content.replace(old_import, new_import)

# 4. Add component before </template>
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

# 5. Write back
with open(r'src/views/tasks/detail.vue', 'w', encoding='utf-8') as f:
    f.write(content)

print("✓ Component added successfully")
print("✓ File saved as UTF-8")
