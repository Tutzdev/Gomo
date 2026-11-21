-- Celular que a pessoa autorizou para receber os avisos de preço. Trocar o número renova o consentimento,
-- então avisos criados antes dele nunca vão para o número novo.
CREATE TABLE whatsapp_contacts (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    phone_number VARCHAR(16) NOT NULL CHECK (phone_number ~ '^\+55[1-9]{2}9[0-9]{8}$'),
    consented_at TIMESTAMPTZ NOT NULL
);

-- Uma linha por aviso já tentado no WhatsApp: enviado (sent_at) ou com a próxima tentativa marcada.
CREATE TABLE whatsapp_deliveries (
    notification_id UUID PRIMARY KEY REFERENCES alert_notifications(id) ON DELETE CASCADE,
    attempts INTEGER NOT NULL CHECK (attempts > 0),
    sent_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    last_error VARCHAR(300)
);

CREATE INDEX idx_alert_notifications_created ON alert_notifications(created_at);
