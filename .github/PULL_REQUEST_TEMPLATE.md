## Summary
Describe the changes made in this pull request and why they are needed.

## Related Issues
Closes #

## Type of Change
- [ ] Bug fix (non-breaking change that fixes an issue)
- [ ] New feature (non-breaking change that adds capability)
- [ ] Refactor / cleanup (no functional change)
- [ ] Documentation / repository polish

## Verification Checklist
- [ ] `./gradlew :app:testDebugUnitTest` passes with zero failures
- [ ] `./gradlew :app:assembleDebug` compiles cleanly
- [ ] No hardcoded API keys, secrets, or tokens
- [ ] Privacy/security guards (`AppBlocklistPolicy`, `PublicHttpSafetyGuard`, `PromptSecuritySanitizer`, confirmation gates) preserved
- [ ] UI changes use `JarvisThemeTokens` / Material 3 theme tokens, `48dp` touch targets, `contentDescription`, and `testTag`
- [ ] Documentation (`README.md`, `CHANGELOG.md`, `docs/*`) updated if user-facing behavior changed
