import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import release_baidu_sync as rs

class ReleaseSyncTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.apk = Path(self.temp.name) / 'shangke-v1.2.3-arm64-v8a-release.apk'
        self.apk.write_bytes(b'published apk')
        self.release = {'isDraft': True, 'isPrerelease': False, 'assets': [{'name': self.apk.name, 'size': self.apk.stat().st_size, 'digest': 'sha256:' + hashlib.sha256(self.apk.read_bytes()).hexdigest()}]}
        self.events = []
    def tearDown(self): self.temp.cleanup()
    def promote(self): self.events.append('promote')
    def sync(self): self.events.append('sync')
    def test_publish_then_sync_in_order(self):
        rs.publish_then_sync(self.apk, self.release, self.promote, self.sync)
        self.assertEqual(self.events, ['promote', 'sync'])
    def test_already_published_resumes_without_promoting(self):
        self.release['isDraft'] = False
        rs.publish_then_sync(self.apk, self.release, self.promote, self.sync)
        self.assertEqual(self.events, ['sync'])
    def test_bad_asset_never_promotes_or_syncs(self):
        self.release['assets'][0]['digest'] = 'sha256:' + '0'*64
        with self.assertRaises(rs.SyncError): rs.publish_then_sync(self.apk, self.release, self.promote, self.sync)
        self.assertEqual(self.events, [])
    def test_prerelease_never_promotes_or_syncs(self):
        self.release['isPrerelease'] = True
        with self.assertRaises(rs.SyncError): rs.publish_then_sync(self.apk, self.release, self.promote, self.sync)
        self.assertEqual(self.events, [])
    def test_promotion_failure_does_not_sync(self):
        def fail(): self.events.append('promote'); raise rs.SyncError('promotion failed')
        with self.assertRaises(rs.SyncError): rs.publish_then_sync(self.apk, self.release, fail, self.sync)
        self.assertEqual(self.events, ['promote'])
    def test_sync_failure_reports_release_already_published(self):
        def fail(): self.events.append('sync'); raise rs.SyncError('sync failed')
        with self.assertRaisesRegex(rs.SyncError, '已发布.*同步失败'):
            rs.publish_then_sync(self.apk, self.release, self.promote, fail)
        self.assertEqual(self.events, ['promote', 'sync'])

    def test_command_checks_auth_then_promotes_and_syncs_without_deploy(self):
        site = Path(self.temp.name) / 'site.js'
        site.write_text("const SITE = {\n  version: '1.2.3',\n  baidu: 'https://pan.baidu.com/s/existing?pwd=a1b2',\n};\n", encoding='utf-8')
        def command(args, description):
            self.events.append(args)
            if 'whoami' in args: return json.dumps({'authenticated': True, 'has_valid_token': True})
            if 'view' in args: return json.dumps(self.release)
            return ''
        argv = ['release_baidu_sync.py', '--version', '1.2.3', '--apk', str(self.apk)]
        with patch.object(sys, 'argv', argv), patch.object(rs.baidu, 'SITE', site), patch.object(rs.baidu, 'find_bdpan', return_value='bdpan'), patch.object(rs.shutil, 'which', return_value='gh'), patch.object(rs, 'run_checked', side_effect=command):
            rs.main()
        self.assertEqual([args[1] for args in self.events[:3]], ['whoami', 'release', 'release'])
        self.assertIn('edit', self.events[2])
        self.assertTrue(self.events[3][1].endswith('baidu_publish_apk.py'))
        self.assertIn('--keep-share', self.events[3])
        self.assertEqual(self.events[0][self.events[0].index('--session-id') + 1], self.events[3][self.events[3].index('--session-id') + 1])
        self.assertFalse(any('wrangler' in str(args) or '--deploy-website' in args for args in self.events))

    def test_invalid_auth_stops_before_release_or_sync(self):
        site = Path(self.temp.name) / 'site.js'
        site.write_text("const SITE = {\n  version: '1.2.3',\n  baidu: 'https://pan.baidu.com/s/existing?pwd=a1b2',\n};\n", encoding='utf-8')
        def command(args, description):
            self.events.append(args)
            return json.dumps({'authenticated': False, 'has_valid_token': False})
        argv = ['release_baidu_sync.py', '--version', '1.2.3', '--apk', str(self.apk)]
        with patch.object(sys, 'argv', argv), patch.object(rs.baidu, 'SITE', site), patch.object(rs.baidu, 'find_bdpan', return_value='bdpan'), patch.object(rs.shutil, 'which', return_value='gh'), patch.object(rs, 'run_checked', side_effect=command):
            with self.assertRaisesRegex(rs.SyncError, '未授权'):
                rs.main()
        self.assertEqual(len(self.events), 1)

    def test_missing_share_stops_before_release_or_sync(self):
        site = Path(self.temp.name) / 'site.js'
        site.write_text("const SITE = {\n  version: '1.2.3',\n  baidu: '',\n};\n", encoding='utf-8')
        argv = ['release_baidu_sync.py', '--version', '1.2.3', '--apk', str(self.apk)]
        with patch.object(sys, 'argv', argv), patch.object(rs.baidu, 'SITE', site), patch.object(rs, 'run_checked') as command:
            with self.assertRaisesRegex(rs.SyncError, '分享配置无效'):
                rs.main()
        command.assert_not_called()

if __name__ == '__main__': unittest.main()
