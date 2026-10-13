/**
 * AirTortilla - Sistema de Gestión y Distribución de Reservas
 * Frontend Vanilla JavaScript con API estándar Fetch
 */

// Estado Global de la Aplicación
const AppState = {
    reservas: [],
    archivos: [],
    coincidencias: [],
    archivoActual: null,
    modalidadTotalDetalle: null,
    lineaActual: 1,
    totalLineasActual: 1,
    autoPlayInterval: null,
    paisesDisponibles: new Set()
};

// Mapeo amigable de banderas por código ISO
const COUNTRY_FLAGS = {
    'ES': '🇪🇸 España',
    'FR': '🇫🇷 Francia',
    'IT': '🇮🇹 Italia',
    'DE': '🇩🇪 Alemania',
    'PT': '🇵🇹 Portugal',
    'US': '🇺🇸 Estados Unidos',
    'JP': '🇯🇵 Japón',
    'MX': '🇲🇽 México',
    'GB': '🇬🇧 Reino Unido'
};

// ==========================================================================
// Inicialización
// ==========================================================================
document.addEventListener('DOMContentLoaded', () => {
    initEventListeners();
    cargarDatosIniciales();
});

function initEventListeners() {
    // Botones de acción principales
    document.getElementById('btn-generar-archivos').addEventListener('click', generarArchivos);

    // Pestañas (Tabs)
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.addEventListener('click', () => cambiarPestana(btn.dataset.tab));
    });

    // Filtros de búsqueda
    document.getElementById('filtro-archivos').addEventListener('input', filtrarArchivos);
    document.getElementById('filtro-reservas').addEventListener('input', filtrarReservas);
    document.getElementById('select-filtro-pais').addEventListener('change', filtrarReservas);

    // Alerta de coincidencias
    document.getElementById('btn-ver-coincidencias').addEventListener('click', () => {
        cambiarPestana('tab-coincidencias');
    });

    // Subida de archivos (Dropzone y File Input)
    const dropzone = document.getElementById('dropzone');
    const fileInput = document.getElementById('input-archivo-csv');
    const btnBrowse = document.getElementById('btn-browse-file');

    btnBrowse.addEventListener('click', (e) => {
        e.stopPropagation();
        fileInput.click();
    });

    dropzone.addEventListener('click', () => fileInput.click());

    dropzone.addEventListener('dragover', (e) => {
        e.preventDefault();
        dropzone.classList.add('drag-over');
    });

    dropzone.addEventListener('dragleave', () => {
        dropzone.classList.remove('drag-over');
    });

    dropzone.addEventListener('drop', (e) => {
        e.preventDefault();
        dropzone.classList.remove('drag-over');
        if (e.dataTransfer.files.length > 0) {
            subirArchivoCsv(e.dataTransfer.files[0]);
        }
    });

    fileInput.addEventListener('change', (e) => {
        if (e.target.files.length > 0) {
            subirArchivoCsv(e.target.files[0]);
            fileInput.value = '';
        }
    });

    // Modales de Consulta
    initModalEventListeners();
}

function initModalEventListeners() {
    // Modal Total
    const modalTotal = document.getElementById('modal-total');
    document.getElementById('btn-cerrar-modal-total').addEventListener('click', () => cerrarModal(modalTotal));
    document.getElementById('btn-cerrar-modal-total-footer').addEventListener('click', () => cerrarModal(modalTotal));
    
    // Toggle Vista Tabla / Raw en Modal Total
    const btnToggleTabla = document.getElementById('btn-toggle-tabla');
    const btnToggleRaw = document.getElementById('btn-toggle-raw');
    const tablaWrapper = document.getElementById('modal-total-tabla-wrapper');
    const rawWrapper = document.getElementById('modal-total-raw-wrapper');

    btnToggleTabla.addEventListener('click', () => {
        btnToggleTabla.classList.add('active');
        btnToggleRaw.classList.remove('active');
        tablaWrapper.style.display = 'block';
        rawWrapper.style.display = 'none';
    });

    btnToggleRaw.addEventListener('click', () => {
        btnToggleRaw.classList.add('active');
        btnToggleTabla.classList.remove('active');
        tablaWrapper.style.display = 'none';
        rawWrapper.style.display = 'block';
    });

    document.getElementById('btn-descargar-archivo-total').addEventListener('click', () => {
        if (AppState.archivoActual) {
            descargarArchivoCsv(AppState.archivoActual);
        }
    });

    // Modal Línea a Línea
    const modalLinea = document.getElementById('modal-linea');
    document.getElementById('btn-cerrar-modal-linea').addEventListener('click', () => {
        detenerAutoPlay();
        cerrarModal(modalLinea);
    });
    document.getElementById('btn-cerrar-modal-linea-footer').addEventListener('click', () => {
        detenerAutoPlay();
        cerrarModal(modalLinea);
    });

    // Controles de navegación Línea a Línea
    document.getElementById('btn-line-first').addEventListener('click', () => irALinea(1));
    document.getElementById('btn-line-last').addEventListener('click', () => irALinea(AppState.totalLineasActual));
    document.getElementById('btn-line-prev').addEventListener('click', () => irALinea(AppState.lineaActual - 1));
    document.getElementById('btn-line-next').addEventListener('click', () => irALinea(AppState.lineaActual + 1));

    const slider = document.getElementById('line-range-slider');
    slider.addEventListener('input', (e) => {
        irALinea(parseInt(e.target.value, 10));
    });

    document.getElementById('btn-line-autoplay').addEventListener('click', alternarAutoPlay);
    document.getElementById('btn-cambiar-a-total').addEventListener('click', () => {
        detenerAutoPlay();
        cerrarModal(modalLinea);
        abrirModalTotal(AppState.archivoActual);
    });

    document.getElementById('btn-copiar-linea').addEventListener('click', () => {
        const text = document.getElementById('card-raw-line').textContent;
        navigator.clipboard.writeText(text).then(() => {
            mostrarToast('Línea copiada al portapapeles', 'success');
        });
    });

    // Cerrar al hacer clic fuera del contenedor modal
    [modalTotal, modalLinea].forEach(modal => {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                detenerAutoPlay();
                cerrarModal(modal);
            }
        });
    });
}

// ==========================================================================
// Carga de Datos desde la API REST (Fetch)
// ==========================================================================
async function cargarDatosIniciales() {
    try {
        await Promise.all([cargarReservasActivas(), cargarListaArchivos()]);
    } catch (err) {
        console.error('Error al inicializar datos:', err);
    }
}

async function cargarReservasActivas() {
    try {
        const res = await fetch('/api/reservas');
        if (!res.ok) throw new Error('Error al obtener reservas');
        const data = await res.json();

        AppState.reservas = data.reservas || [];
        AppState.coincidencias = data.coincidencias || [];

        actualizarKPIs(data);
        renderizarTablaReservas(AppState.reservas);
        renderizarCoincidencias(AppState.coincidencias);
        actualizarFiltroPaises(AppState.reservas);
    } catch (err) {
        mostrarToast('Error al conectar con la API de reservas', 'error');
    }
}

async function cargarListaArchivos() {
    try {
        const res = await fetch('/api/reservas/archivos');
        if (!res.ok) throw new Error('Error al listar archivos');
        const archivos = await res.json();

        AppState.archivos = archivos || [];
        document.getElementById('kpi-total-archivos').textContent = AppState.archivos.length;
        document.getElementById('badge-contador-archivos').textContent = AppState.archivos.length;

        renderizarGridArchivos(AppState.archivos);
    } catch (err) {
        console.error('Error al cargar lista de archivos:', err);
    }
}

async function subirArchivoCsv(file) {
    if (!file.name.toLowerCase().endsWith('.csv')) {
        mostrarToast('Por favor selecciona un archivo con extensión .csv', 'error');
        return;
    }

    const formData = new FormData();
    formData.append('archivo', file);

    try {
        mostrarToast(`Procesando archivo '${file.name}' con OpenCSV 5.9...`, 'info');
        const res = await fetch('/api/reservas/subir', {
            method: 'POST',
            body: formData
        });

        if (!res.ok) {
            const errData = await res.json();
            throw new Error(errData.mensaje || 'Error al procesar el archivo');
        }

        const data = await res.json();
        await cargarDatosIniciales();
        mostrarToast(data.mensaje || 'Archivo procesado correctamente', 'success');
    } catch (err) {
        mostrarToast(err.message, 'error');
    }
}

async function generarArchivos() {
    try {
        mostrarToast('Dividiendo reservas en ficheros por país de destino...', 'info');
        const res = await fetch('/api/reservas/generar-archivos', { method: 'POST' });
        if (!res.ok) throw new Error('Error al generar archivos');
        const data = await res.json();

        await cargarListaArchivos();
        cambiarPestana('tab-archivos');
        mostrarToast(`Se generaron ${data.totalArchivos} archivos correctamente`, 'success');
    } catch (err) {
        mostrarToast('Error al generar archivos: ' + err.message, 'error');
    }
}

// ==========================================================================
// Renderizado y Vistas
// ==========================================================================
function actualizarKPIs(data) {
    document.getElementById('kpi-total-reservas').textContent = data.totalReservas || 0;
    document.getElementById('badge-contador-reservas').textContent = data.totalReservas || 0;

    document.getElementById('kpi-total-paises').textContent = data.totalPaisesDestino || 0;

    const totalCoincidencias = data.totalGruposCoincidentes || 0;
    document.getElementById('kpi-total-coincidencias').textContent = totalCoincidencias;
    document.getElementById('badge-contador-coincidencias').textContent = totalCoincidencias;

    const alertBox = document.getElementById('alert-coincidencias');
    const alertTexto = document.getElementById('alert-coincidencias-texto');

    if (totalCoincidencias > 0) {
        alertBox.style.display = 'flex';
        const nombres = data.coincidencias.map(g => `${g.nombrePasajero} (${g.totalCoincidentes} pax)`).join(', ');
        alertTexto.textContent = `Se detectaron ${totalCoincidencias} grupos con pasajeros coincidentes en Nombre, Origen y Destino: ${nombres}. Cada uno posee localizador e ID inequívoco.`;
    } else {
        alertBox.style.display = 'none';
    }
}

function renderizarGridArchivos(archivos) {
    const grid = document.getElementById('grid-archivos');
    const empty = document.getElementById('empty-archivos');
    const labelCount = document.getElementById('label-archivos-count');

    labelCount.textContent = `Mostrando ${archivos.length} ficheros`;

    if (!archivos || archivos.length === 0) {
        grid.innerHTML = '';
        empty.style.display = 'block';
        return;
    }

    empty.style.display = 'none';
    grid.innerHTML = archivos.map(a => {
        const flagName = COUNTRY_FLAGS[a.codigoPaisDestino] || `🌍 ${a.codigoPaisDestino}`;
        const fechaFormateada = a.fechaReserva || '-';
        const tamanoKb = (a.tamanoBytes / 1024).toFixed(1);

        return `
            <div class="file-card">
                <div>
                    <div class="file-card-header">
                        <span class="file-country-badge">${flagName}</span>
                        <span class="file-records-badge">${a.totalReservas} reservas</span>
                    </div>
                    <div class="file-card-title">${a.nombreArchivo}</div>
                    <div class="file-card-meta">
                        <span>📅 Fecha: <strong>${fechaFormateada}</strong></span>
                        <span>💾 Tamaño: <strong>${tamanoKb} KB</strong></span>
                    </div>
                </div>
                <div class="file-card-actions">
                    <button class="btn btn-secondary btn-sm" onclick="abrirModalTotal('${a.nombreArchivo}')">
                        👁️ Modalidad Total
                    </button>
                    <button class="btn btn-primary btn-sm" onclick="abrirModalLinea('${a.nombreArchivo}', 1)">
                        🔍 Modalidad Línea a Línea
                    </button>
                    <button class="btn btn-ghost btn-xs btn-download" onclick="descargarArchivoCsv('${a.nombreArchivo}')">
                        📥 Descargar Fichero CSV
                    </button>
                </div>
            </div>
        `;
    }).join('');
}

function renderizarTablaReservas(reservas) {
    const tbody = document.getElementById('tbody-reservas');
    if (!reservas || reservas.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" style="text-align:center; padding:30px; color:#94a3b8;">No hay reservas para mostrar.</td></tr>';
        return;
    }

    // Detectar claves de coincidencia
    const mapaCoincidentes = new Map();
    AppState.coincidencias.forEach(g => {
        mapaCoincidentes.set(g.clave, g.totalCoincidentes);
    });

    tbody.innerHTML = reservas.map(r => {
        const clave = `${(r.nombrePasajero||'').trim().toLowerCase()}||${(r.origen||'').trim().toLowerCase()}||${(r.destino||'').trim().toLowerCase()}`;
        const esCoincidente = mapaCoincidentes.has(clave);
        const totalCoincidentes = mapaCoincidentes.get(clave);

        const badgeEstado = esCoincidente
            ? `<span class="badge-collision-alert" title="Comparte nombre, origen y destino">⚠️ Coincidente (${totalCoincidentes} pax)</span>`
            : `<span style="color:#10b981; font-weight:600; font-size:0.8rem;">✓ Única</span>`;

        return `
            <tr>
                <td><span class="badge-id">${r.id || '-'}</span></td>
                <td><strong>${escapeHtml(r.nombrePasajero || '')}</strong></td>
                <td>${escapeHtml(r.origen || '')}</td>
                <td>${escapeHtml(r.destino || '')}</td>
                <td><strong>${r.codigoPaisDestino || '-'}</strong></td>
                <td>${r.fechaReserva || '-'}</td>
                <td><span class="badge-locator">${r.localizador || '-'}</span></td>
                <td>${r.asiento || '-'}</td>
                <td>${badgeEstado}</td>
            </tr>
        `;
    }).join('');
}

function renderizarCoincidencias(coincidencias) {
    const container = document.getElementById('lista-coincidencias');
    if (!coincidencias || coincidencias.length === 0) {
        container.innerHTML = '<div class="empty-state"><p>No se encontraron pasajeros coincidentes en el dataset actual.</p></div>';
        return;
    }

    container.innerHTML = coincidencias.map((g, idx) => {
        return `
            <div class="coincidencia-card">
                <div class="coincidencia-header">
                    <div>
                        <span class="coincidencia-title">Caso ${idx + 1}: ${escapeHtml(g.nombrePasajero)}</span>
                        <div style="font-size:0.85rem; color:#94a3b8; margin-top:4px;">
                            Ruta: <strong>${escapeHtml(g.origen)}</strong> ➔ <strong>${escapeHtml(g.destino)}</strong> (${g.codigoPaisDestino})
                        </div>
                    </div>
                    <span class="coincidencia-count-badge">${g.totalCoincidentes} Pasajeros Coincidentes</span>
                </div>
                <div class="coincidencia-table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>#</th>
                                <th>ID Inequívoco</th>
                                <th>Localizador Asignado</th>
                                <th>Fecha Reserva</th>
                                <th>Asiento</th>
                                <th>Precio</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${g.reservas.map((r, i) => `
                                <tr>
                                    <td>${i + 1}</td>
                                    <td><span class="badge-id">${r.id}</span></td>
                                    <td><span class="badge-locator">${r.localizador}</span></td>
                                    <td>${r.fechaReserva}</td>
                                    <td>${r.asiento || '-'}</td>
                                    <td>${r.precio ? r.precio + ' €' : '-'}</td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
            </div>
        `;
    }).join('');
}

function actualizarFiltroPaises(reservas) {
    const select = document.getElementById('select-filtro-pais');
    const paises = new Set();
    reservas.forEach(r => {
        if (r.codigoPaisDestino) paises.add(r.codigoPaisDestino);
    });

    const valorPrevio = select.value;
    select.innerHTML = '<option value="">Todos los países de destino</option>' +
        Array.from(paises).sort().map(p => {
            const label = COUNTRY_FLAGS[p] || p;
            return `<option value="${p}">${label}</option>`;
        }).join('');

    select.value = valorPrevio;
}

// ==========================================================================
// Filtros en Tiempo Real
// ==========================================================================
function filtrarArchivos() {
    const query = document.getElementById('filtro-archivos').value.toLowerCase().trim();
    if (!query) {
        renderizarGridArchivos(AppState.archivos);
        return;
    }

    const filtrados = AppState.archivos.filter(a =>
        a.nombreArchivo.toLowerCase().includes(query) ||
        a.codigoPaisDestino.toLowerCase().includes(query) ||
        (a.fechaReserva && a.fechaReserva.includes(query))
    );
    renderizarGridArchivos(filtrados);
}

function filtrarReservas() {
    const query = document.getElementById('filtro-reservas').value.toLowerCase().trim();
    const paisSeleccionado = document.getElementById('select-filtro-pais').value;

    const filtradas = AppState.reservas.filter(r => {
        const matchPais = !paisSeleccionado || r.codigoPaisDestino === paisSeleccionado;
        const matchQuery = !query ||
            (r.nombrePasajero && r.nombrePasajero.toLowerCase().includes(query)) ||
            (r.origen && r.origen.toLowerCase().includes(query)) ||
            (r.destino && r.destino.toLowerCase().includes(query)) ||
            (r.id && r.id.toLowerCase().includes(query)) ||
            (r.localizador && r.localizador.toLowerCase().includes(query));
        return matchPais && matchQuery;
    });

    renderizarTablaReservas(filtradas);
}

// ==========================================================================
// Modalidad Total (Full File Viewer)
// ==========================================================================
async function abrirModalTotal(nombreArchivo) {
    AppState.archivoActual = nombreArchivo;
    const modal = document.getElementById('modal-total');

    try {
        const res = await fetch(`/api/reservas/archivos/${encodeURIComponent(nombreArchivo)}/total`);
        if (!res.ok) throw new Error('No se pudo consultar el archivo en modalidad total');
        const data = await res.json();

        AppState.modalidadTotalDetalle = data;

        document.getElementById('modal-total-titulo').textContent = data.info.nombreArchivo;
        document.getElementById('modal-total-metadatos').innerHTML = `
            <div class="meta-item"><span class="meta-label">País ISO:</span> <span class="meta-value">${data.info.codigoPaisDestino}</span></div>
            <div class="meta-item"><span class="meta-label">Fecha:</span> <span class="meta-value">${data.info.fechaReserva}</span></div>
            <div class="meta-item"><span class="meta-label">Total Reservas:</span> <span class="meta-value">${data.info.totalReservas}</span></div>
            <div class="meta-item"><span class="meta-label">Tamaño:</span> <span class="meta-value">${(data.info.tamanoBytes / 1024).toFixed(2)} KB</span></div>
        `;

        // Renderizar tabla
        const tbody = document.getElementById('modal-total-tbody');
        tbody.innerHTML = (data.reservas || []).map((r, i) => `
            <tr>
                <td>${i + 1}</td>
                <td><span class="badge-id">${r.id}</span></td>
                <td><strong>${escapeHtml(r.nombrePasajero)}</strong></td>
                <td>${escapeHtml(r.origen)}</td>
                <td>${escapeHtml(r.destino)}</td>
                <td>${r.codigoPaisDestino}</td>
                <td>${r.fechaReserva}</td>
                <td><span class="badge-locator">${r.localizador}</span></td>
                <td>${r.asiento || '-'}</td>
                <td>${r.precio ? r.precio + ' €' : '-'}</td>
            </tr>
        `).join('');

        // Renderizar raw CSV
        document.getElementById('modal-total-raw-code').textContent = data.contenidoRaw || '';

        // Resetear tab a vista tabla
        document.getElementById('btn-toggle-tabla').click();

        abrirModal(modal);
    } catch (err) {
        mostrarToast('Error al abrir modalidad total: ' + err.message, 'error');
    }
}

// ==========================================================================
// Modalidad Línea a Línea (Line-by-Line Viewer)
// ==========================================================================
async function abrirModalLinea(nombreArchivo, numeroLinea = 1) {
    AppState.archivoActual = nombreArchivo;
    AppState.lineaActual = numeroLinea;
    const modal = document.getElementById('modal-linea');

    document.getElementById('modal-linea-titulo').textContent = `Explorador Línea a Línea: ${nombreArchivo}`;
    await irALinea(numeroLinea);
    abrirModal(modal);
}

async function irALinea(numero) {
    if (!AppState.archivoActual) return;

    try {
        const res = await fetch(`/api/reservas/archivos/${encodeURIComponent(AppState.archivoActual)}/linea?numero=${numero}`);
        if (!res.ok) throw new Error('Error al consultar línea');
        const data = await res.json();

        AppState.lineaActual = data.numeroLinea;
        AppState.totalLineasActual = data.totalLineas;

        // Actualizar Stepper & Slider
        document.getElementById('line-counter-badge').textContent = `Línea ${data.numeroLinea} de ${data.totalLineas}`;
        const slider = document.getElementById('line-range-slider');
        slider.min = 1;
        slider.max = data.totalLineas;
        slider.value = data.numeroLinea;

        // Botones de navegación
        document.getElementById('btn-line-prev').disabled = !data.tieneAnterior;
        document.getElementById('btn-line-first').disabled = data.numeroLinea === 1;
        document.getElementById('btn-line-next').disabled = !data.tieneSiguiente;
        document.getElementById('btn-line-last').disabled = data.numeroLinea === data.totalLineas;

        // Datos de la Reserva
        const r = data.reserva || {};
        document.getElementById('card-pax-name').textContent = r.nombrePasajero || '-';
        document.getElementById('card-flight-route').textContent = `Vuelo: ${r.origen || '-'} ➔ ${r.destino || '-'}`;
        document.getElementById('card-pax-locator').textContent = r.localizador || '-';
        document.getElementById('card-pax-id').textContent = r.id || '-';
        document.getElementById('card-pax-iso').textContent = r.codigoPaisDestino || '-';
        document.getElementById('card-pax-date').textContent = r.fechaReserva || '-';
        document.getElementById('card-pax-seat').textContent = `${r.asiento || 'Sin asignar'} (${r.precio ? r.precio + ' €' : '-'})`;

        // Alerta de colisión
        const collisionAlert = document.getElementById('card-collision-alert');
        const collisionText = document.getElementById('card-collision-text');
        if (data.esCoincidente) {
            collisionAlert.style.display = 'flex';
            collisionText.textContent = `Comparte nombre, origen y destino con otras ${data.totalCoincidentes - 1} reservas. Su localizador inequívoco '${r.localizador}' garantiza la desambiguación.`;
        } else {
            collisionAlert.style.display = 'none';
        }

        // Línea CSV cruda
        document.getElementById('card-raw-line').textContent = data.lineaRaw || '';

    } catch (err) {
        mostrarToast('Error al consultar línea: ' + err.message, 'error');
    }
}

function alternarAutoPlay() {
    const btn = document.getElementById('btn-line-autoplay');
    if (AppState.autoPlayInterval) {
        detenerAutoPlay();
    } else {
        btn.textContent = '⏸️ Pausar';
        btn.classList.add('btn-primary');
        AppState.autoPlayInterval = setInterval(() => {
            if (AppState.lineaActual >= AppState.totalLineasActual) {
                irALinea(1);
            } else {
                irALinea(AppState.lineaActual + 1);
            }
        }, 1500);
    }
}

function detenerAutoPlay() {
    if (AppState.autoPlayInterval) {
        clearInterval(AppState.autoPlayInterval);
        AppState.autoPlayInterval = null;
        const btn = document.getElementById('btn-line-autoplay');
        if (btn) {
            btn.textContent = '▶ Auto-Play';
            btn.classList.remove('btn-primary');
        }
    }
}

// ==========================================================================
// Utilidades de Interfaz (Tabs, Modales, Toasts)
// ==========================================================================
function cambiarPestana(tabId) {
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.tab === tabId);
    });
    document.querySelectorAll('.tab-content').forEach(section => {
        section.classList.toggle('active', section.id === tabId);
    });
}

function abrirModal(modal) {
    modal.style.display = 'flex';
    document.body.style.overflow = 'hidden';
}

function cerrarModal(modal) {
    modal.style.display = 'none';
    document.body.style.overflow = 'auto';
}

function descargarArchivoCsv(nombreArchivo) {
    window.location.href = `/api/reservas/archivos/${encodeURIComponent(nombreArchivo)}/descargar`;
}

function mostrarToast(mensaje, tipo = 'info') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast toast-${tipo}`;

    const icon = tipo === 'success' ? '✓' : tipo === 'error' ? '✕' : 'ℹ';
    toast.innerHTML = `<span style="font-weight:bold;">${icon}</span> <span>${escapeHtml(mensaje)}</span>`;

    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transition = 'opacity 0.3s ease';
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
