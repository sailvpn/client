const express = require('express');
const bcrypt = require('bcrypt');
const { generateToken } = require('../utils/jwt');

const router = express.Router();

// Mock user database - replace with your actual database
const users = [
  {
    id: 1,
    username: 'admin',
    // Password: 'password' - generate with: bcrypt.hashSync('password', 10)
    password: '$2b$10$K7L1OJ45/4Y2nIvhRVpCe.FGKdOhKOmWZl3JQhBZqVKnqK0YUJZsK'
  }
];

router.post('/login', async (req, res) => {
  const { username, password } = req.body;

  if (!username || !password) {
    return res.status(400).json({ error: 'Username and password required' });
  }

  const user = users.find(u => u.username === username);

  if (!user) {
    return res.status(401).json({ error: 'Invalid credentials' });
  }

  const validPassword = await bcrypt.compare(password, user.password);

  if (!validPassword) {
    return res.status(401).json({ error: 'Invalid credentials' });
  }

  const token = generateToken({
    userId: user.id,
    username: user.username
  });

  res.json({
    token,
    username: user.username,
    userId: user.id
  });
});

module.exports = router;
