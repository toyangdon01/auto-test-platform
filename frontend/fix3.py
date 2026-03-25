import re
import os

os.chdir(r"C:\Users\Administrator\.openclaw\workspace\products\auto-test-platform\frontend")

file_path = r"src/views/tasks/detail.vue"

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. 添加导入
old_import = "import { ref, computed, onMounted, onUnmounted } from 'vue'"
new_import = "import { ref, computed, onMounted, onUnmounted } from 'vue'\nimport MetricTimeseriesChart from '@/components/MetricTimeseriesChart.vue'"
content = content.replace(old_import, new_import)

# 2. 找到主内容区域的最后，在</div>（main 区域的结束）之前添加
# 查找 "导出指标" 按钮后面的区域
old_pattern = """        </div>
      </div>
    </div>

    <!-- 步骤详情弹窗 -->"""

new_pattern = """        </div>
      </div>
    </div>

    <!-- 指标采集时序图 -->
    <div class="page-card mt-20" v-if="task?.collectEnabled">
      <h4 class="section-title">
        <el-icon><DataLine /></el-icon>
        指标采集趋势
      </h4>
      <MetricTimeseriesChart :task-id="task.id" />
    </div>

    <!-- 步骤详情弹窗 -->"""

content = content.replace(old_pattern, new_pattern)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Component added successfully in correct position")
