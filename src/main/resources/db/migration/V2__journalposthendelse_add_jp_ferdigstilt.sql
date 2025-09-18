ALTER TABLE journalposthendelse
    ADD COLUMN IF NOT EXISTS journalpost_ferdigstilt_tidspunkt timestamp with time zone;