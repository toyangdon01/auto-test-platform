#!/usr/bin/env python3
import psycopg2

conn = psycopg2.connect(host='localhost', database='test_platform', user='postgres')
cursor = conn.cursor()

# 检查 tasks 表
cursor.execute('SELECT id, name, status FROM tasks WHERE id IN (631, 632)')
print('Tasks:')
for row in cursor.fetchall():
    print(row)

# 检查 task_steps 表
cursor.execute('SELECT id, task_id, step_name FROM task_steps WHERE task_id IN (631, 632)')
print('\nTask steps:')
for row in cursor.fetchall():
    print(row)

# 检查 servers 表中 id=-1 的记录
cursor.execute("SELECT id, name, ip_address FROM servers WHERE id = -1")
print('\nServer id=-1:')
print(cursor.fetchall())

conn.close()