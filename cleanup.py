#!/usr/bin/env python3
import sys

# 读取文件
with open('./auto-test-platform/backend/src/main/java/com/autotest/service/impl/OnlineImportServiceImpl.java', 'r') as f:
    lines = f.readlines()

# 保留 1-621 行和最后 19 行
new_lines = lines[:621] + lines[-19:]

# 写回文件
with open('./auto-test-platform/backend/src/main/java/com/autotest/service/impl/OnlineImportServiceImpl.java', 'w') as f:
    f.writelines(new_lines)

print(f"删除完成，新行数: {len(new_lines)}")
