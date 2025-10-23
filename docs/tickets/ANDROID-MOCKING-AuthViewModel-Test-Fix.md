# TICKET: Fix AuthViewModel Test - Android TextUtils Mocking Issue

**Status:** 🔴 Open  
**Priority:** Low  
**Type:** Bug Fix / Test Maintenance  
**Estimated Effort:** 1-2 hours  
**Assignee:** TBD  
**Created:** 2025-10-23

---

## Problem Summary

One unit test in `AuthViewModelTest` is failing due to Android framework dependency:

```
Test: register with valid data creates user and Firestore doc
Status: FAILED
Error: java.lang.RuntimeException: Method isEmpty in android.text.TextUtils not mocked
```

**Impact:**
- Test suite at 98% pass rate (71/72 tests)
- Blocks achieving 100% test coverage
- Pre-existing issue (not introduced by recent changes)

---

## Root Cause Analysis

### Technical Details

**Failing Test Location:**
- File: `app/src/test/java/com/messageai/tactical/ui/auth/AuthViewModelTest.kt`
- Test: `register with valid data creates user and Firestore doc`
- Line: ~125 (in the lambda/listener callback)

**Error Stack Trace:**
```
java.lang.RuntimeException: Method isEmpty in android.text.TextUtils not mocked.
See https://developer.android.com/r/studio-ui/build/not-mocked for details.
	at android.text.TextUtils.isEmpty(TextUtils.java)
	at com.google.firebase.auth.UserProfileChangeRequest.<init>(com.google.firebase:firebase-auth@@23.0.0:10)
	at com.google.firebase.auth.UserProfileChangeRequest$Builder.build(com.google.firebase:firebase-auth@@23.0.0:11)
	at com.messageai.tactical.ui.auth.AuthViewModel.register$lambda$7(AuthViewModel.kt:54)
```

**Root Cause:**
The production code in `AuthViewModel.register()` creates a `UserProfileChangeRequest` which internally calls `android.text.TextUtils.isEmpty()`. This Android framework method is not available in JVM unit tests.

**Affected Production Code:**
```kotlin
// AuthViewModel.kt around line 54
val profileUpdates = UserProfileChangeRequest.Builder()
    .setDisplayName(displayName)  // ← This internally uses TextUtils.isEmpty()
    .build()
```

---

## Reproduction Steps

1. Run the test suite:
   ```bash
   ./gradlew :app:testDevDebugUnitTest
   ```

2. Observe failure:
   ```
   AuthViewModelTest > register with valid data creates user and Firestore doc FAILED
       java.lang.RuntimeException at AuthViewModelTest.kt:125
   ```

3. Full test report available at:
   ```
   app/build/reports/tests/testDevDebugUnitTest/index.html
   ```

---

## Solution Options

### Option 1: Add Robolectric (Recommended)

**What:** Use Robolectric to provide Android framework implementations in tests

**Pros:**
- Comprehensive solution for Android framework dependencies
- Allows testing of Android-specific behavior
- Industry standard for Android unit testing

**Cons:**
- Slightly slower test execution
- Additional dependency

**Implementation:**

1. Add Robolectric dependency to `app/build.gradle.kts`:
```kotlin
testImplementation("org.robolectric:robolectric:4.11.1")
```

2. Update `AuthViewModelTest.kt`:
```kotlin
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthViewModelTest {
    // ... existing tests
}
```

3. Verify test passes:
```bash
./gradlew :app:testDevDebugUnitTest --tests "*.AuthViewModelTest"
```

---

### Option 2: Configure Android Mocking in Gradle

**What:** Configure Gradle to return default values for Android framework methods

**Pros:**
- No additional dependencies
- Simple configuration

**Cons:**
- May hide real issues with Android framework usage
- Less robust than Robolectric

**Implementation:**

Add to `app/build.gradle.kts`:
```kotlin
android {
    // ... existing config
    
    testOptions {
        unitTests.returnDefaultValues = true
    }
}
```

**Note:** This is a band-aid solution and may not work for all cases.

---

### Option 3: Refactor Production Code (Not Recommended)

**What:** Refactor `AuthViewModel` to avoid `TextUtils.isEmpty()`

**Pros:**
- Removes Android dependency from business logic

**Cons:**
- Changes working production code for test purposes
- `UserProfileChangeRequest` still uses `TextUtils` internally
- Doesn't solve the fundamental issue

**Not recommended** because the issue is in Firebase Auth SDK, not our code.

---

## Recommended Solution: Option 1 (Robolectric)

### Implementation Steps

1. **Add Robolectric dependency**
   ```kotlin
   // In app/build.gradle.kts
   dependencies {
       // ... existing dependencies
       testImplementation("org.robolectric:robolectric:4.11.1")
   }
   ```

2. **Update AuthViewModelTest**
   ```kotlin
   import org.junit.runner.RunWith
   import org.robolectric.RobolectricTestRunner
   import org.robolectric.annotation.Config
   
   @RunWith(RobolectricTestRunner::class)
   @Config(sdk = [28]) // Or your minSdk
   class AuthViewModelTest {
       // ... existing code unchanged
   }
   ```

3. **Verify fix**
   ```bash
   # Run just this test class
   ./gradlew :app:testDevDebugUnitTest --tests "*.AuthViewModelTest"
   
   # Verify all tests pass
   ./gradlew :app:testDevDebugUnitTest
   ```

4. **Expected result**
   ```
   72 tests completed, 0 failed
   Success rate: 100%
   ```

---

## Acceptance Criteria

- [ ] `AuthViewModelTest.register with valid data creates user and Firestore doc` passes
- [ ] All other tests still pass (maintain 71/72 → 72/72)
- [ ] Test execution time doesn't increase significantly (< 2s added)
- [ ] No warnings or deprecation notices
- [ ] Build and lint checks pass
- [ ] Documentation updated if needed

---

## Testing Checklist

After implementing the fix:

```bash
# 1. Clean build
./gradlew clean

# 2. Run all unit tests
./gradlew :app:testDevDebugUnitTest

# 3. Verify test report
# Open: app/build/reports/tests/testDevDebugUnitTest/index.html
# Expected: 72 tests, 0 failures

# 4. Run lint
./gradlew :app:lintDevDebug

# 5. Verify compilation
./gradlew :app:compileDevDebugKotlin
```

---

## Related Files

### Files to Modify
- `app/build.gradle.kts` - Add Robolectric dependency
- `app/src/test/java/com/messageai/tactical/ui/auth/AuthViewModelTest.kt` - Add annotations

### Files to Review
- `app/src/main/java/com/messageai/tactical/ui/auth/AuthViewModel.kt` - Production code (no changes needed)

### Related Documentation
- Pre-existing test issues report: `docs/reviews/PRE_EXISTING_TEST_ISSUES_REPORT.md`
- Block A QC report: `docs/reviews/BLOCK_A_QC_REPORT.md`

---

## Context & Background

### When Did This Break?
This test has been failing since it was written. It was masked by other test compilation issues that were recently fixed (Google Truth imports, RootViewModel signature).

### Why Wasn't It Caught Earlier?
- Test suite wasn't running during MVP sprint (24-hour deadline)
- Other test failures masked this issue
- No CI/CD pipeline configured yet

### Why Fix Now?
- Other test issues are resolved (98% → 100%)
- Blocking clean test suite
- Good practice for future Android-dependent tests
- Low risk, high value fix

---

## Additional Notes

### Robolectric Configuration (Optional)

If you encounter issues with Robolectric, you can configure it further:

```kotlin
// In app/src/test/resources/robolectric.properties
sdk=28
manifest=--none
```

### Alternative Test Runners

If Robolectric causes issues, consider:
- AndroidJUnit4 with instrumented tests (slower but comprehensive)
- PowerMock (more complex, generally not recommended)

### Future Considerations

Once Robolectric is configured:
- Consider migrating other Android-dependent tests
- Document Robolectric usage in project README
- Add to testing best practices guide

---

## Success Metrics

✅ **Definition of Done:**
1. Test passes consistently
2. Test suite at 100% (72/72 tests)
3. No new warnings or errors
4. Clean git commit with descriptive message
5. Update ticket status to CLOSED

---

## Questions or Issues?

If you encounter problems:

1. **Robolectric version conflicts:**
   - Try matching to your compile SDK version
   - Check Robolectric compatibility matrix

2. **Test still fails:**
   - Check you're using the correct test runner
   - Verify annotation placement
   - Try specifying explicit SDK version in @Config

3. **Performance issues:**
   - Consider Option 2 (Gradle config) as fallback
   - Profile test execution times

---

## Estimated Breakdown

- Add dependency: 2 minutes
- Update test file: 5 minutes
- Run tests and verify: 10 minutes
- Debug if needed: 30 minutes
- Documentation: 15 minutes
- **Total: ~1 hour** (with buffer for issues)

---

## References

- [Robolectric Official Docs](http://robolectric.org/)
- [Android Testing Guide - Unit Tests](https://developer.android.com/training/testing/unit-testing/local-unit-tests)
- [Firebase Auth Testing](https://firebase.google.com/docs/auth/android/start)
- [Why Android Mocking Matters](https://developer.android.com/r/studio-ui/build/not-mocked)

---

**Created by:** QC Agent (Block A Review)  
**Related Sprint:** Sprint 2 - AI Integration  
**Blocks:** None (low priority)  
**Ticket ID:** ANDROID-MOCKING-001

