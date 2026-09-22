from pathlib import Path
import tempfile
import unittest
from uuid import uuid4

from fastapi.testclient import TestClient
from core.config import Settings
from server.api import create_app


class ApiTests(unittest.TestCase):
    def test_sim_readiness_requires_auth_and_does_not_claim_phone_audio(self):
        self.assertEqual(self.client.get("/v1/sim/readiness").status_code, 401)
        result = self.client.get("/v1/sim/readiness", headers=self.headers)
        self.assertEqual(result.status_code, 200)
        self.assertEqual(result.json()["sim_audio_transport"], "NOT_CONNECTED")
        self.assertEqual(result.json()["conversation"], "UNAVAILABLE")
        self.assertFalse(result.json()["models_loaded"])
        self.assertFalse(result.json()["paid_services_required"])
        self.assertNotIn(self.config.api_token, result.text)
        self.assertNotIn(str(self.config.vosk_model_path), result.text)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.config = Settings(api_token="test-token-" * 4, reports_path=Path(self.temp.name) / "reports.sqlite3")
        self.client = TestClient(create_app(self.config))
        self.client.__enter__()
        self.headers = {"Authorization": f"Bearer {self.config.api_token}"}
        self.report = {"id": str(uuid4()), "caller": "Demo contact", "outcome": "Neo took a message.",
                       "reply": "Hello", "message": "Call me later", "time": 123456, "demo": True}

    def tearDown(self):
        self.client.__exit__(None, None, None)
        self.temp.cleanup()

    def test_health_has_no_secrets(self):
        result = self.client.get("/health")
        self.assertEqual(result.status_code, 200)
        self.assertNotIn(self.config.api_token, result.text)

    def test_browser_root_explains_service_without_exposing_private_data(self):
        result = self.client.get("/")
        self.assertEqual(result.status_code, 200)
        self.assertEqual(result.json()["health_endpoint"], "/health")
        self.assertNotIn(self.config.api_token, result.text)

    def test_all_private_operations_require_auth(self):
        for method, path in [("GET", "/v1/policy"), ("GET", "/v1/reports"),
                             ("POST", "/v1/reports"), ("DELETE", f"/v1/reports/{uuid4()}")]:
            self.assertEqual(self.client.request(method, path).status_code, 401)
        self.assertEqual(self.client.get("/v1/policy", headers={"Authorization": "Bearer wrong"}).status_code, 401)

    def test_policy_matches_contact_only_six_second_demo(self):
        policy = self.client.get("/v1/policy", headers=self.headers).json()
        self.assertEqual(policy["answer_delay_ms"], 6000)
        self.assertTrue(policy["contacts_only"])
        self.assertFalse(policy["real_calls_enabled"])

    def test_report_roundtrip_retry_and_delete(self):
        for _ in range(2):
            self.assertEqual(self.client.post("/v1/reports", json=self.report, headers=self.headers).status_code, 200)
        items = self.client.get("/v1/reports", headers=self.headers).json()["reports"]
        self.assertEqual(items, [self.report])
        self.client.delete(f'/v1/reports/{self.report["id"]}', headers=self.headers)
        self.assertEqual(self.client.get("/v1/reports", headers=self.headers).json()["reports"], [])

    def test_conflicting_retry_does_not_overwrite(self):
        self.client.post("/v1/reports", json=self.report, headers=self.headers)
        changed = {**self.report, "message": "Different"}
        self.assertEqual(self.client.post("/v1/reports", json=changed, headers=self.headers).status_code, 409)

    def test_validation_rejects_real_calls_and_oversized_messages(self):
        for change in [{"demo": False}, {"message": "x" * 4001}, {"id": "../escape"}, {"time": -1}]:
            self.assertEqual(self.client.post("/v1/reports", json={**self.report, **change}, headers=self.headers).status_code, 422)

    def test_reports_persist_across_app_instances(self):
        self.client.post("/v1/reports", json=self.report, headers=self.headers)
        with TestClient(create_app(self.config)) as other:
            self.assertEqual(other.get("/v1/reports", headers=self.headers).json()["reports"], [self.report])


if __name__ == "__main__":
    unittest.main()
