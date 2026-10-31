import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import baidu_publish_apk as bp

class FakeDrive:
    def __init__(self, upload=None, share=None):
        self.upload_result = upload or {'code': 0, 'data': {'action': 'created', 'remote_path': '上课/app.apk'}}
        self.share_result = share or {'link': 'https://pan.baidu.com/s/1test', 'pwd': 'a1b2', 'period': 0}
        self.calls = []
    def upload(self, apk, remote):
        self.calls.append(('upload', remote))
        return self.upload_result
    def share(self, remote):
        self.calls.append(('share', remote))
        return self.share_result

class PublishTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.site = Path(self.tmp.name) / 'site.js'
        self.site.write_text("const SITE = {\n  baidu: '',\n};\n", encoding='utf-8')
        self.apk = Path(self.tmp.name) / 'app.apk'
        self.apk.write_bytes(b'APK')
    def tearDown(self): self.tmp.cleanup()
    def test_upload_share_updates_site_with_password(self):
        drive = FakeDrive()
        bp.publish(drive, self.apk, '上课/app.apk', self.site)
        self.assertEqual(drive.calls, [('upload', '上课/app.apk'), ('share', '上课')])
        self.assertIn("baidu: 'https://pan.baidu.com/s/1test?pwd=a1b2'", self.site.read_text(encoding='utf-8'))
    def test_sync_preserves_user_selected_share_without_creating_new_share(self):
        original = "const SITE = {\n  baidu: 'https://pan.baidu.com/s/16oiKPYuVN2s_QokkEkjrnQ?pwd=wpw6',\n};\n"
        self.site.write_text(original, encoding='utf-8')
        drive = FakeDrive()
        link, actual = bp.publish(drive, self.apk, '上课/app.apk', self.site, reuse_existing=True)
        self.assertEqual(drive.calls, [('upload', '上课/app.apk')])
        self.assertEqual(link, 'https://pan.baidu.com/s/16oiKPYuVN2s_QokkEkjrnQ?pwd=wpw6')
        self.assertEqual(self.site.read_text(encoding='utf-8'), original)
    def test_sync_missing_share_fails_before_upload(self):
        drive = FakeDrive()
        with self.assertRaises(bp.PublishError): bp.publish(drive, self.apk, '上课/app.apk', self.site, reuse_existing=True)
        self.assertEqual(drive.calls, [])
    def test_uses_actual_renamed_path(self):
        drive = FakeDrive(upload={'code': 0, 'data': {'action': 'renamed', 'remote_path': '上课/app_1.apk'}})
        bp.publish(drive, self.apk, '上课/app.apk', self.site)
        self.assertEqual(drive.calls[-1], ('share', '上课'))
    def test_absolute_app_path_is_converted_to_cli_relative_path(self):
        drive = FakeDrive(upload={'code': 0, 'data': {'action': 'created', 'remote_path': '/apps/bdpan/上课/app.apk'}})
        bp.publish(drive, self.apk, '上课/app.apk', self.site)
        self.assertEqual(drive.calls[-1], ('share', '上课'))
    def test_upload_path_outside_authorized_root_is_rejected(self):
        drive = FakeDrive(upload={'code': 0, 'data': {'action': 'created', 'remote_path': '/apps/other/app.apk'}})
        with self.assertRaises(bp.PublishError): bp.publish(drive, self.apk, '上课/app.apk', self.site)
        self.assertEqual(len(drive.calls), 1)
    def test_failed_or_unconfirmed_upload_does_not_share(self):
        for upload in [{'code': 1, 'error': 'failed'}, {'code': 0, 'data': {'action': 'unknown'}}]:
            drive = FakeDrive(upload=upload)
            with self.assertRaises(bp.PublishError): bp.publish(drive, self.apk, '上课/app.apk', self.site)
            self.assertEqual(len(drive.calls), 1)
            self.assertIn("baidu: ''", self.site.read_text(encoding='utf-8'))
    def test_invalid_share_does_not_modify_site(self):
        for share in [{'link': 'https://example.com/s/abc', 'pwd': 'a1b2'}, {'link': 'https://pan.baidu.com/s/1test', 'pwd': 'a1b2', 'period': 7}]:
            with self.assertRaises(bp.PublishError): bp.publish(FakeDrive(share=share), self.apk, '上课/app.apk', self.site)
            self.assertIn("baidu: ''", self.site.read_text(encoding='utf-8'))
    def test_rejects_other_versions_or_bad_hash(self):
        release = {'isDraft': False, 'isPrerelease': False, 'assets': [{'name': self.apk.name, 'size': 3, 'digest': 'sha256:' + '0'*64}]}
        with self.assertRaises(bp.PublishError): bp.verify_release(self.apk, release)

    def test_finds_windows_cli_before_current_process_path_refreshes(self):
        exe = Path(self.tmp.name) / 'bdpan' / 'bdpan.exe'
        exe.parent.mkdir()
        exe.touch()
        with patch.object(bp.shutil, 'which', return_value=None), patch.dict(bp.os.environ, {'LOCALAPPDATA': self.tmp.name}):
            self.assertEqual(bp.find_bdpan(), str(exe))

if __name__ == '__main__': unittest.main()
