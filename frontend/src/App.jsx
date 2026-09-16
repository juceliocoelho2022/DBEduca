import { useEffect, useMemo, useState } from 'react'
import { api } from './api.js'

const fallbackEngines = [
  { id: 'POSTGRESQL', name: 'PostgreSQL', category: 'SQL relacional' },
  { id: 'MYSQL', name: 'MySQL', category: 'SQL relacional' },
  { id: 'MONGODB', name: 'MongoDB', category: 'NoSQL documental' }
]

const typeOptions = ['BIGINT', 'INTEGER', 'VARCHAR(100)', 'VARCHAR(150)', 'TEXT', 'DATE', 'TIMESTAMP', 'BOOLEAN', 'DECIMAL(10,2)']
const newColumn = () => ({ name: '', type: 'VARCHAR(100)', primaryKey: false, notNull: false, unique: false })

export default function App() {
  const [dark, setDark] = useState(() => localStorage.getItem('dbeduca-theme') === 'dark')
  const [engines, setEngines] = useState(fallbackEngines)
  const [engine, setEngine] = useState('POSTGRESQL')
  const [tableName, setTableName] = useState('alunos')
  const [columns, setColumns] = useState([
    { name: 'id', type: 'BIGINT', primaryKey: true, notNull: true, unique: false },
    { name: 'nome', type: 'VARCHAR(100)', primaryKey: false, notNull: true, unique: false },
    { name: 'email', type: 'VARCHAR(150)', primaryKey: false, notNull: false, unique: true }
  ])
  const [script, setScript] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    document.documentElement.dataset.theme = dark ? 'dark' : 'light'
    localStorage.setItem('dbeduca-theme', dark ? 'dark' : 'light')
  }, [dark])

  useEffect(() => {
    api.listEngines().then(setEngines).catch(() => setEngines(fallbackEngines))
  }, [])

  const selectedEngine = useMemo(
    () => engines.find(item => item.id === engine) ?? fallbackEngines[0],
    [engines, engine]
  )

  function updateColumn(index, field, value) {
    setColumns(current => current.map((column, i) => i === index ? { ...column, [field]: value } : column))
  }

  function removeColumn(index) {
    setColumns(current => current.length === 1 ? current : current.filter((_, i) => i !== index))
  }

  async function generate() {
    setError('')
    setScript('')
    setLoading(true)
    try {
      const result = await api.generateScript({ engine, tableName, columns })
      setScript(result.script)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function copyScript() {
    if (script) await navigator.clipboard.writeText(script)
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-icon">▰</span>
          <div><strong>DBEduca</strong><small>Database Learning Lab</small></div>
        </div>
        <nav>
          <a className="active">◫ Modelador</a>
          <a className="disabled">⌘ Editor SQL/NoSQL <span>Sprint 2</span></a>
          <a className="disabled">✓ Atividades <span>Sprint 3</span></a>
          <a className="disabled">✦ Tutor IA <span>Sprint 4</span></a>
          <a className="disabled">▦ Turmas <span>Sprint 3</span></a>
        </nav>
        <div className="sidebar-card">
          <b>Perfis planejados</b>
          <p>Administrador · Professor · Aluno</p>
        </div>
      </aside>

      <main>
        <header className="topbar">
          <div>
            <p className="eyebrow">LABORATÓRIO EDUCACIONAL</p>
            <h1>Modele seu banco de dados</h1>
            <p>Defina a estrutura e veja o script que seria utilizado em cada tecnologia.</p>
          </div>
          <button className="theme-button" onClick={() => setDark(v => !v)} aria-label="Alternar tema">
            {dark ? '☀ Claro' : '☾ Escuro'}
          </button>
        </header>

        <section className="engine-grid">
          {engines.map(item => (
            <button key={item.id} className={`engine-card ${engine === item.id ? 'selected' : ''}`} onClick={() => setEngine(item.id)}>
              <span className={`engine-dot ${item.id.toLowerCase()}`}></span>
              <b>{item.name}</b>
              <small>{item.category}</small>
              {engine === item.id && <em>Selecionado</em>}
            </button>
          ))}
        </section>

        <div className="workspace-grid">
          <section className="panel model-panel">
            <div className="panel-heading">
              <div><span className="step">1</span><div><h2>Estrutura</h2><p>{selectedEngine.name}</p></div></div>
              <button className="ghost-button" onClick={() => setColumns(current => [...current, newColumn()])}>+ Campo</button>
            </div>

            <label className="field-label">Nome da tabela / collection</label>
            <input className="text-input" value={tableName} onChange={e => setTableName(e.target.value)} placeholder="ex.: alunos" />

            <div className="columns-header">
              <span>Campo</span><span>Tipo</span><span>PK</span><span>NN</span><span>UQ</span><span></span>
            </div>

            <div className="columns-list">
              {columns.map((column, index) => (
                <div className="column-row" key={index}>
                  <input value={column.name} onChange={e => updateColumn(index, 'name', e.target.value)} placeholder="nome_campo" />
                  <select value={column.type} onChange={e => updateColumn(index, 'type', e.target.value)}>
                    {typeOptions.map(type => <option key={type}>{type}</option>)}
                  </select>
                  <input type="checkbox" checked={column.primaryKey} onChange={e => updateColumn(index, 'primaryKey', e.target.checked)} title="Primary Key" />
                  <input type="checkbox" checked={column.notNull} onChange={e => updateColumn(index, 'notNull', e.target.checked)} title="Not Null" />
                  <input type="checkbox" checked={column.unique} onChange={e => updateColumn(index, 'unique', e.target.checked)} title="Unique" />
                  <button className="remove-button" onClick={() => removeColumn(index)} title="Remover campo">×</button>
                </div>
              ))}
            </div>

            <button className="primary-button" onClick={generate} disabled={loading}>
              {loading ? 'Gerando…' : 'Gerar script'}
            </button>
            {error && <div className="error-box">{error}</div>}
          </section>

          <section className="panel script-panel">
            <div className="panel-heading">
              <div><span className="step">2</span><div><h2>Script gerado</h2><p>Revise antes de executar</p></div></div>
              <button className="ghost-button" disabled={!script} onClick={copyScript}>Copiar</button>
            </div>

            <div className="code-window">
              <div className="code-top"><span></span><span></span><span></span><b>{selectedEngine.name}</b></div>
              <pre>{script || '// Configure os campos ao lado e clique em “Gerar script”.'}</pre>
            </div>

            <div className="safety-note">
              <strong>Modo seguro do MVP</strong>
              <p>Esta versão gera e valida scripts, mas ainda não executa comandos arbitrários. A execução isolada entra no próximo incremento.</p>
            </div>
          </section>
        </div>

        <section className="roadmap">
          <div><b>MVP</b><span>Modelagem + geração multi-banco</span></div>
          <div><b>Sprint 2</b><span>Execução isolada + editor</span></div>
          <div><b>Sprint 3</b><span>Login, perfis, turmas e avaliação</span></div>
          <div><b>Sprint 4</b><span>Tutor IA híbrido</span></div>
        </section>
      </main>
    </div>
  )
}
