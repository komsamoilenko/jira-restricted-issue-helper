# Service Management only: portal, share and moved cards, no restricted card.
# For instances that do not use issue security levels, or do not want the
# helper to talk about them at all. No internal-viewer policy is needed.
name = jsm-only
modules = jsm portal share moved
MODE_ORDER = ['portal', 'share', 'moved']
