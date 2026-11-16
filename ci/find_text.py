"""Print the center x y of the first uiautomator node whose text contains argv[2]."""
import re, sys, xml.etree.ElementTree as ET

tree = ET.parse(sys.argv[1])
needle = sys.argv[2]
for node in tree.iter("node"):
    if needle in node.get("text", ""):
        l, t, r, b = map(int, re.findall(r"\d+", node.get("bounds")))
        print((l + r) // 2, (t + b) // 2)
        sys.exit(0)
sys.exit(1)
