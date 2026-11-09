from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
required = ['core/domain', 'core/audio', 'core/permissions', 'platform/androidactions', 'app']
for module in required:
    reports = list((root / module / 'build/test-results').glob('*/TEST-*.xml'))
    suites = [ET.parse(p).getroot() for p in reports]
    count = sum(int(s.get('tests', '0')) for s in suites)
    assert count > 0, f'No tests executed for {module}'
    assert all(int(s.get('failures', '0')) + int(s.get('errors', '0')) == 0 for s in suites), module
    print(f'{module}: {count} tests passed')
