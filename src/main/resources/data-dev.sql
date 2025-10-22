-- Seed Data for H2 Development Database
-- This file runs automatically when using the 'dev' profile
-- Spring Boot will execute this after Hibernate creates the schema

-- ============================================
-- Unit Types (Volume and Weight)
-- ============================================

INSERT INTO unit_type (name, abbreviation, is_volume) VALUES
('Barrels', 'bbls', true),
('Gallons', 'gal', true),
('Liters', 'L', true),
('Grams', 'g', false),
('Kilograms', 'kg', false),
('Pounds', 'lbs', false),
('Ounces', 'oz', false);

-- ============================================
-- Transaction Types
-- ============================================

-- Volume transactions (affect tank quantity)
INSERT INTO transaction_type (type_name, description, unit_id, affects_tank_quantity) VALUES
('Transfer In', 'Transfer from previous tank or brew kettle', 1, true),
('Transfer Out', 'Transfer to bright tank or next stage', 1, true),
('Waste', 'Waste or drain from tank', 1, true),
('Sample', 'Sample taken for testing', 1, true);

-- Weight transactions (do not affect tank quantity)
INSERT INTO transaction_type (type_name, description, unit_id, affects_tank_quantity) VALUES
('Yeast Addition', 'Add yeast to start fermentation', 4, false),
('Lysozyme Addition', 'Add lysozyme for stability', 4, false),
('Nutrient Addition', 'Add yeast nutrients', 4, false);

-- Note/observation transactions (use minimal unit, don't affect quantity)
INSERT INTO transaction_type (type_name, description, unit_id, affects_tank_quantity) VALUES
('Temperature Reading', 'Record temperature observation', 4, false),
('pH Reading', 'Record pH measurement', 4, false),
('Gravity Reading', 'Record specific gravity', 4, false),
('Note', 'General note or observation', 4, false);

-- ============================================
-- Sample Fermenter Tanks for Testing
-- ============================================

INSERT INTO ferm_tank (label, current_quantity, capacity, capacity_unit_id, created_at) VALUES
('FV-1', 90.00, 100.00, 1, CURRENT_TIMESTAMP),
('FV-2', 36.00, 80.00, 1, CURRENT_TIMESTAMP),
('FV-3', 20.00, 100.00, 1, CURRENT_TIMESTAMP),
('FV-4', 0.00, 120.00, 1, CURRENT_TIMESTAMP),
('FV-5', 0.00, 100.00, 1, CURRENT_TIMESTAMP),
('FV-6', 80.00, 80.00, 1, CURRENT_TIMESTAMP);

-- ============================================
-- Sample Batches (for tanks with active batches)
-- ============================================

INSERT INTO ferm_batch (tank_id, batch_name, start_date, yeast_date, lysozyme_date, created_at) VALUES
(1, 'Left Turn IPA', DATEADD('DAY', -12, CURRENT_TIMESTAMP), DATEADD('DAY', -12, CURRENT_TIMESTAMP), DATEADD('DAY', -11, CURRENT_TIMESTAMP), DATEADD('DAY', -12, CURRENT_TIMESTAMP)),
(2, 'Honey Crisp Cider', DATEADD('DAY', -5, CURRENT_TIMESTAMP), DATEADD('DAY', -5, CURRENT_TIMESTAMP), NULL, DATEADD('DAY', -5, CURRENT_TIMESTAMP)),
(3, 'Barrel Aged Stout', DATEADD('DAY', -28, CURRENT_TIMESTAMP), DATEADD('DAY', -28, CURRENT_TIMESTAMP), DATEADD('DAY', -27, CURRENT_TIMESTAMP), DATEADD('DAY', -28, CURRENT_TIMESTAMP)),
(6, 'Pilsner', DATEADD('DAY', -3, CURRENT_TIMESTAMP), DATEADD('DAY', -3, CURRENT_TIMESTAMP), NULL, DATEADD('DAY', -3, CURRENT_TIMESTAMP));

-- Update tanks with current batch IDs
UPDATE ferm_tank SET current_batch_id = 1 WHERE id = 1;
UPDATE ferm_tank SET current_batch_id = 2 WHERE id = 2;
UPDATE ferm_tank SET current_batch_id = 3 WHERE id = 3;
UPDATE ferm_tank SET current_batch_id = 4 WHERE id = 6;

-- ============================================
-- Sample Transactions (for FV-1 / Left Turn IPA)
-- ============================================

-- Initial transfer
INSERT INTO ferm_transaction (batch_id, transaction_type_id, quantity, transaction_date, user_id, notes) VALUES
(1, 1, 92.00, DATEADD('DAY', -12, CURRENT_TIMESTAMP), 1, 'Initial transfer from brew kettle');

-- Yeast addition
INSERT INTO ferm_transaction (batch_id, transaction_type_id, quantity, transaction_date, user_id, notes) VALUES
(1, 5, 200.00, DATEADD('HOUR', -11, DATEADD('DAY', -12, CURRENT_TIMESTAMP)), 1, 'Pitched yeast strain WLP001');

-- Lysozyme addition
INSERT INTO ferm_transaction (batch_id, transaction_type_id, quantity, transaction_date, user_id, notes) VALUES
(1, 6, 50.00, DATEADD('DAY', -11, CURRENT_TIMESTAMP), 2, 'Added lysozyme for stability');

-- Sample taken
INSERT INTO ferm_transaction (batch_id, transaction_type_id, quantity, transaction_date, user_id, notes) VALUES
(1, 4, 2.00, DATEADD('HOUR', -2, CURRENT_TIMESTAMP), 1, 'Daily quality check');

-- ============================================
-- Sample Transactions (for FV-2 / Honey Crisp Cider)
-- ============================================

INSERT INTO ferm_transaction (batch_id, transaction_type_id, quantity, transaction_date, user_id, notes) VALUES
(2, 1, 40.00, DATEADD('DAY', -5, CURRENT_TIMESTAMP), 1, 'Transfer from pressing'),
(2, 5, 150.00, DATEADD('HOUR', -4, DATEADD('DAY', -5, CURRENT_TIMESTAMP)), 2, 'Pitched cider yeast'),
(2, 4, 4.00, DATEADD('DAY', -1, CURRENT_TIMESTAMP), 1, 'Sample for gravity reading');

-- ============================================
-- Sample Transactions (for FV-3 / Barrel Aged Stout)
-- ============================================

INSERT INTO ferm_transaction (batch_id, transaction_type_id, quantity, transaction_date, user_id, notes) VALUES
(3, 1, 100.00, DATEADD('DAY', -28, CURRENT_TIMESTAMP), 1, 'Initial transfer'),
(3, 5, 300.00, DATEADD('HOUR', -6, DATEADD('DAY', -28, CURRENT_TIMESTAMP)), 1, 'Pitched yeast for high gravity'),
(3, 6, 75.00, DATEADD('DAY', -27, CURRENT_TIMESTAMP), 2, 'Added lysozyme'),
(3, 3, 80.00, DATEADD('DAY', -14, CURRENT_TIMESTAMP), 1, 'Transferred most volume to barrels');

-- ============================================
-- Sample Transactions (for FV-6 / Pilsner)
-- ============================================

INSERT INTO ferm_transaction (batch_id, transaction_type_id, quantity, transaction_date, user_id, notes) VALUES
(4, 1, 80.00, DATEADD('DAY', -3, CURRENT_TIMESTAMP), 1, 'Initial transfer from brew kettle'),
(4, 5, 180.00, DATEADD('HOUR', -5, DATEADD('DAY', -3, CURRENT_TIMESTAMP)), 2, 'Pitched lager yeast');
