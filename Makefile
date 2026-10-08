.PHONY: run
run:
	mvn -q compile exec:java

.PHONY: check
check:
	@echo "compile-check — see .github/workflows/ci.yml for the full matrix"
