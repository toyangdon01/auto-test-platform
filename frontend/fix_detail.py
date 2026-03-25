import re

file_path = r"C:\Users\Administrator\.openclaw\workspace\products\auto-test-platform\frontend\src\views\tasks\detail.vue"

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. 添加导入
old_import = "import { ref, computed, onMounted, onUnmounted } from 'vue'"
new_import = "import { ref, computed, onMounted, onUnmounted } from 'vue'\nimport MetricTimeseriesChart from '@/components/MetricTimeseriesChart.vue'"

content = content.replace(old_import, new_import)

# 2. 在</template>前添加指标采集卡片
old_end = "  </div>\n</template>"
new_end = """  </div>

  <!-- 指标采集时序图 -->
  <div class="page-card mt-20" v-if="task?.collectEnabled">
    <h4 class="section-title">
      <el-icon><DataLine /></el-icon>
      指标采集趋势
    </h4>
    <MetricTimeseriesChart :task-id="task.id" />
  </div>
</template>"""

content = content.replace(old_end, new_end)

with open(file_path, 'w', encoding='utf-8', newline='') as f:
    f.write(content)

print("✓ 已添加指标采集时序图组件")
