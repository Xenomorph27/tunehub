"""Prints "x y" for the centre of the first uiautomator node matching the query.

usage: find_node.py dump.xml (text|prefix|contains|desc|class) VALUE

Compose merges a row's texts into one node ("Title, Subtitle"), so "text" also
matches the first part of such a merged label.
"""
import re
import sys
import xml.etree.ElementTree as ET

path, mode, value = sys.argv[1], sys.argv[2], sys.argv[3]
for node in ET.parse(path).iter("node"):
    text = node.get("text", "")
    desc = node.get("content-desc", "")
    hit = (
        (mode == "text" and (text == value or text.startswith(value + ", ")))
        or (mode == "prefix" and text.startswith(value))
        or (mode == "contains" and value in text)
        or (mode == "desc" and desc == value)
        or (mode == "class" and node.get("class") == value)
    )
    if hit:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        sys.exit(0)
sys.exit(1)
