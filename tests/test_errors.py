import unittest

from fastapi import FastAPI
from fastapi.testclient import TestClient
from server.errors import install_error_handlers


class ErrorTests(unittest.TestCase):
    def test_unexpected_failure_has_correlated_sanitized_log(self):
        app = FastAPI()
        install_error_handlers(app)

        @app.get('/fail')
        def fail():
            raise RuntimeError('private-token-and-transcript')

        with TestClient(app, raise_server_exceptions=False) as client:
            with self.assertLogs('neo.errors', level='ERROR') as logs:
                result = client.get('/fail?token=private-query')
        self.assertEqual(result.status_code, 500)
        problem = result.json()['error']
        self.assertEqual(problem['code'], 'API_INTERNAL')
        self.assertIn(problem['reference'], logs.output[0])
        self.assertNotIn('private-', result.text + logs.output[0])

    def test_missing_route_has_actionable_response(self):
        app = FastAPI()
        install_error_handlers(app)
        with TestClient(app) as client:
            result = client.get('/missing')
        self.assertEqual(result.status_code, 404)
        self.assertTrue(result.json()['error']['action'])
