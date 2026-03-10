.PHONY: up down logs clean test

up:
	docker compose up --build -d
	@echo ""
	@echo "TaskForge running:"
	@echo "  Dashboard : http://localhost:3000"
	@echo "  API       : http://localhost:8080"
	@echo "  API Health: http://localhost:8080/actuator/health"

down:
	docker compose down

logs:
	docker compose logs -f

logs-api:
	docker compose logs -f api

logs-workers:
	docker compose logs -f worker-1 worker-2 worker-3

clean:
	docker compose down -v --remove-orphans

submit:
	curl -s -X POST http://localhost:8080/api/v1/jobs \
	  -H "Content-Type: application/json" \
	  -d '{"type":"email","queuePriority":"HIGH","maxRetries":3}' | python3 -m json.tool

stats:
	curl -s http://localhost:8080/api/v1/jobs/stats | python3 -m json.tool

jobs:
	curl -s http://localhost:8080/api/v1/jobs | python3 -m json.tool
