-- Spring Security 7 changes serialized authentication compatibility. Require a fresh sign-in
-- once on upgrade rather than trying to deserialize security contexts from an older generation.
DELETE FROM SPRING_SESSION;
