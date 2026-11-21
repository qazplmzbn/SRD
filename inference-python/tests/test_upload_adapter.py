"""Unit-test the upload adapter without loading checkpoints or dataset files."""
import ast
from pathlib import Path
from types import SimpleNamespace
from unittest import TestCase, main
from unittest.mock import Mock

class UploadAdapterTest(TestCase):
    def setUp(self):
        tree = ast.parse((Path(__file__).parents[1] / 'app.py').read_text(encoding='utf-8-sig'))
        fn = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'infer_emocc_upload')
        fn.decorator_list = []
        fn.args.args[0].annotation = None
        module = ast.fix_missing_locations(ast.Module(body=[fn], type_ignores=[]))
        self.service = Mock(is_loaded=True, _loaded_dataset='weibo')
        self.service.predict_single_user.return_value = SimpleNamespace(risk_class=1, risk_level='medium', confidence=.7, post_attention_scores=[], model_info={'class_probs':[.3,.7]})
        self.env = {'get_emocc_service':lambda:self.service, 'load_emocc_model':Mock(return_value=True), '_ok':lambda d:{'success':True,'data':d}, '_fail':lambda s:{'success':False,'error':s}, 'traceback':Mock()}
        exec(compile(module, '<upload-adapter>', 'exec'), self.env)
        self.body = SimpleNamespace(userHash='new-user',dataSource='weibo',bertEmbeddings=[[.2]*768],emojiSequences=[[]],posts=['new text'])
    def test_passes_uploaded_features_without_dataset_lookup(self):
        result = self.env['infer_emocc_upload'](self.body)
        self.assertTrue(result['success'])
        args = self.service.predict_single_user.call_args.args
        self.assertEqual(args[0], 'new-user')
        self.assertEqual(args[1].shape, (1,768))
        self.assertEqual(args[3], ['new text'])
    def test_rejects_bad_dimensions_and_alignment(self):
        self.body.bertEmbeddings=[[.2]]
        self.assertFalse(self.env['infer_emocc_upload'](self.body)['success'])
        self.service.predict_single_user.assert_not_called()
        self.body.bertEmbeddings=[[.2]*768];self.body.posts=[]
        self.assertFalse(self.env['infer_emocc_upload'](self.body)['success'])
        self.service.predict_single_user.assert_not_called()
    def test_failure_does_not_generate_rule_result(self):
        self.service.predict_single_user.side_effect=RuntimeError('missing checkpoint')
        self.assertFalse(self.env['infer_emocc_upload'](self.body)['success'])
if __name__ == '__main__': main()
