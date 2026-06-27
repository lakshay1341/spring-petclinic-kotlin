CREATE TABLE IF NOT EXISTS vets (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  first_name VARCHAR(30),
  last_name VARCHAR(30),
  INDEX(last_name)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS specialties (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80),
  INDEX(name)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS vet_specialties (
  vet_id INT(4) UNSIGNED NOT NULL,
  specialty_id INT(4) UNSIGNED NOT NULL,
  FOREIGN KEY (vet_id) REFERENCES vets(id),
  FOREIGN KEY (specialty_id) REFERENCES specialties(id),
  UNIQUE (vet_id,specialty_id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS types (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80),
  INDEX(name)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS owners (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  first_name VARCHAR(30),
  last_name VARCHAR(30),
  address VARCHAR(255),
  city VARCHAR(80),
  telephone VARCHAR(20),
  INDEX(last_name)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS pets (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(30),
  birth_date DATE,
  type_id INT(4) UNSIGNED NOT NULL,
  owner_id INT(4) UNSIGNED NOT NULL,
  INDEX(name),
  FOREIGN KEY (owner_id) REFERENCES owners(id),
  FOREIGN KEY (type_id) REFERENCES types(id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS visits (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  pet_id INT(4) UNSIGNED NOT NULL,
  visit_date DATE,
  description VARCHAR(255),
  FOREIGN KEY (pet_id) REFERENCES pets(id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS pet_admissions (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  pet_id INT(4) UNSIGNED NOT NULL,
  status VARCHAR(20) NOT NULL,
  admitted_at DATETIME NOT NULL,
  discharged_at DATETIME,
  ward VARCHAR(80),
  cage VARCHAR(80),
  device_uuid VARCHAR(100),
  responsible_vet_id INT(4) UNSIGNED,
  latest_heart_rate INT,
  last_vital_at DATETIME,
  INDEX(pet_id),
  INDEX(device_uuid),
  FOREIGN KEY (pet_id) REFERENCES pets(id) ON DELETE RESTRICT
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS adt_events (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  correlation_id VARCHAR(100) NOT NULL UNIQUE,
  admission_id INT(4) UNSIGNED NOT NULL,
  event_type VARCHAR(20) NOT NULL,
  pet_id INT(4) UNSIGNED NOT NULL,
  occurred_at DATETIME NOT NULL,
  FOREIGN KEY (admission_id) REFERENCES pet_admissions(id) ON DELETE RESTRICT
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS vital_samples (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  admission_id INT(4) UNSIGNED NOT NULL,
  metric VARCHAR(20) NOT NULL,
  sample_value INT,
  observation_id VARCHAR(100) UNIQUE,
  sampled_at DATETIME NOT NULL,
  INDEX(admission_id, sampled_at),
  FOREIGN KEY (admission_id) REFERENCES pet_admissions(id) ON DELETE RESTRICT
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS alarm_limits (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  admission_id INT(4) UNSIGNED NOT NULL,
  metric VARCHAR(20) NOT NULL,
  low_extreme INT NOT NULL,
  low INT NOT NULL,
  high INT NOT NULL,
  high_extreme INT NOT NULL,
  INDEX(admission_id),
  FOREIGN KEY (admission_id) REFERENCES pet_admissions(id) ON DELETE RESTRICT
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS alarm_events (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  admission_id INT(4) UNSIGNED NOT NULL,
  metric VARCHAR(20) NOT NULL,
  level VARCHAR(10) NOT NULL,
  state VARCHAR(10) NOT NULL,
  started_at DATETIME NOT NULL,
  ended_at DATETIME,
  trigger_value INT,
  acked_by VARCHAR(80),
  acked_at DATETIME,
  silenced_until DATETIME,
  silenced_by VARCHAR(80),
  INDEX(admission_id, state),
  FOREIGN KEY (admission_id) REFERENCES pet_admissions(id) ON DELETE RESTRICT
) engine=InnoDB;
