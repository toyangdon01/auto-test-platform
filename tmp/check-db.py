#!/usr/bin/env python3
import sqlite3

conn = sqlite3.connect('C:/Users/Administrator/.autotest/test_platform.db')
cursor = conn.cursor()

# 检查 task_steps 表结构
cursor.execute("PRAGMA table_info(task_steps)")
print('Task_steps table structure:')
for row in cursor.fetchall():
    print(row)

# 检查外键约束
cursor.execute("PRAGMA foreign_key_list(task_steps)")
print('\nForeign keys:')
for row in cursor.fetchall():
    print(row)

# 检查 task_servers 表
cursor.execute("SELECT * FROM task_servers WHERE task_id IN (631, 632)")
print('\nTask servers:')
for row in cursor.fetchall():
    print(row)

conn.close()