-- Minimal Seed Data for H2 Development Database
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
-- Transaction Types (Fermenter-Specific Set)
-- ============================================
-- IMPORTANT: IDs must match frontend JavaScript in fermenters.html
-- quantity_multiplier: 1 for additions, -1 for removals, 0 for non-quantity events
-- NOTE: All volume-based types use GALLONS (unit_id = 2)

-- ID 1: Cider Addition (affects tank quantity - adds volume)
-- Used for both initial fill AND subsequent additions during fermentation
INSERT INTO transaction_type (id, type_name, description, unit_id, affects_tank_quantity, quantity_multiplier) VALUES
(1, 'Cider Addition', 'Add apple cider to fermenter', 2, true, 1);

-- ID 2: Yeast Addition (does not affect tank quantity - sets yeastDate)
INSERT INTO transaction_type (id, type_name, description, unit_id, affects_tank_quantity, quantity_multiplier) VALUES
(2, 'Yeast Addition', 'Pitch yeast to start fermentation', 4, false, 0);

-- ID 3: Lysozyme Addition (does not affect tank quantity - sets lysozymeDate)
INSERT INTO transaction_type (id, type_name, description, unit_id, affects_tank_quantity, quantity_multiplier) VALUES
(3, 'Lysozyme Addition', 'Add lysozyme enzyme', 4, false, 0);

-- ID 4: Transfer Out (affects tank quantity - removes volume)
-- Moves finished product to bright tank or another fermenter
INSERT INTO transaction_type (id, type_name, description, unit_id, affects_tank_quantity, quantity_multiplier) VALUES
(4, 'Transfer Out', 'Transfer to another tank', 2, true, -1);

-- ID 5: Sample (affects tank quantity - removes small volume)
INSERT INTO transaction_type (id, type_name, description, unit_id, affects_tank_quantity, quantity_multiplier) VALUES
(5, 'Sample', 'Sample for testing', 2, true, -1);

-- ID 6: Waste (affects tank quantity - removes volume)
INSERT INTO transaction_type (id, type_name, description, unit_id, affects_tank_quantity, quantity_multiplier) VALUES
(6, 'Waste', 'Trub/sediment removal', 2, true, -1);

-- ID 7: Note (does not affect tank quantity)
INSERT INTO transaction_type (id, type_name, description, unit_id, affects_tank_quantity, quantity_multiplier) VALUES
(7, 'Note', 'General note or observation', 4, false, 0);

-- ID 8: Transfer In (affects tank quantity - adds volume)
-- Receives transfer from another fermenter tank
INSERT INTO transaction_type (id, type_name, description, unit_id, affects_tank_quantity, quantity_multiplier) VALUES
(8, 'Transfer In', 'Received transfer from another fermenter', 2, true, 1);

-- ============================================
-- Sample Fermenter Tanks for Testing
-- ============================================
-- NOTE: All capacities and quantities are in GALLONS

-- Empty tank for testing "Start Batch" functionality
-- 100 bbls = 3200 gallons (1 bbl = 32 gal)
INSERT INTO ferm_tank (label, current_quantity, capacity, created_at) VALUES
('FV-1', 0.00, 3200.00, CURRENT_TIMESTAMP);

-- Tank with active batch for testing "Add Transaction" functionality
-- 50 bbls = 1600 gallons
INSERT INTO ferm_tank (label, current_quantity, capacity, created_at) VALUES
('FV-2', 1600.00, 3200.00, CURRENT_TIMESTAMP);

-- ============================================
-- Sample Batch (for FV-2)
-- ============================================

INSERT INTO ferm_batch (tank_id, batch_name, start_date, created_at) VALUES
(2, 'Test Batch', DATEADD('DAY', -2, CURRENT_TIMESTAMP), DATEADD('DAY', -2, CURRENT_TIMESTAMP));

-- Update FV-2 with current batch ID
UPDATE ferm_tank SET current_batch_id = 1 WHERE id = 2;

-- ============================================
-- Sample Transactions (for FV-2 / Test Batch)
-- ============================================
-- NOTE: All quantities are in GALLONS (quantity_unit_id = 2)

-- Initial cider addition (ID 1) - 50 bbls = 1600 gallons
INSERT INTO ferm_transaction (batch_id, transaction_type_id, quantity, quantity_unit_id, transaction_date, user_id, notes) VALUES
(1, 1, 1600.00, 2, DATEADD('DAY', -2, CURRENT_TIMESTAMP), 1, 'Initial cider addition - Honeycrisp blend');

-- Sample note (ID 7) - notes don't have quantity
INSERT INTO ferm_transaction (batch_id, transaction_type_id, quantity, quantity_unit_id, transaction_date, user_id, notes) VALUES
(1, 7, 0.00, 2, DATEADD('DAY', -1, CURRENT_TIMESTAMP), 1, 'Fermentation proceeding normally');
