/**
 * AirTortilla - Sistema de Gestión y Distribución de Reservas
 * Frontend Vanilla JavaScript con API estándar Fetch
 */

// Estado Global de la Aplicación
const AppState = {
    usuarioLogueado: null,
    usuarios: [],
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

function onEl(id, event, handler) {
    const el = document.getElementById(id);
    if (el) el.addEventListener(event, handler);
}

// Inicialización
document.addEventListener('DOMContentLoaded', () => {
    initAuthEventListeners();
    initEventListeners();
    initUsuarioEventListeners();
    verificarSesionInicial();
});

function initEventListeners() {
    // Botones de acción principales
    onEl('btn-generar-archivos', 'click', generarArchivos);

    // Pestañas (Tabs)
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.addEventListener('click', () => cambiarPestana(btn.dataset.tab));
    });

    // Filtros de búsqueda
    onEl('filtro-archivos', 'input', filtrarArchivos);
    onEl('filtro-reservas', 'input', filtrarReservas);
    onEl('select-filtro-pais', 'change', filtrarReservasPorPais);

    // Vaciar base de datos de reservas
    onEl('btn-vaciar-reservas', 'click', vaciarReservasBd);

    // Subida de archivos (Dropzone y File Input)
    const dropzone = document.getElementById('dropzone');
    const fileInput = document.getElementById('input-archivo-csv');
    const btnBrowse = document.getElementById('btn-browse-file');

    if (btnBrowse && fileInput) {
        btnBrowse.addEventListener('click', (e) => {
            e.stopPropagation();
            fileInput.click();
        });
    }

    if (dropzone && fileInput) {
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
    }

    if (fileInput) {
        fileInput.addEventListener('change', (e) => {
            if (e.target.files.length > 0) {
                subirArchivoCsv(e.target.files[0]);
                fileInput.value = '';
            }
        });
    }

    // Modales de Consulta
    initModalEventListeners();
}

function initModalEventListeners() {
    // Modal Total
    const modalTotal = document.getElementById('modal-total');
    onEl('btn-cerrar-modal-total', 'click', () => cerrarModal(modalTotal));
    onEl('btn-cerrar-modal-total-footer', 'click', () => cerrarModal(modalTotal));

    // Toggle Vista Tabla / Raw en Modal Total
    const btnToggleTabla = document.getElementById('btn-toggle-tabla');
    const btnToggleRaw = document.getElementById('btn-toggle-raw');
    const tablaWrapper = document.getElementById('modal-total-tabla-wrapper');
    const rawWrapper = document.getElementById('modal-total-raw-wrapper');

    if (btnToggleTabla && btnToggleRaw && tablaWrapper && rawWrapper) {
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
    }

    onEl('btn-descargar-archivo-total', 'click', () => {
        if (AppState.archivoActual) {
            descargarArchivoCsv(AppState.archivoActual);
        }
    });

    // Modal Línea a Línea
    const modalLinea = document.getElementById('modal-linea');
    onEl('btn-cerrar-modal-linea', 'click', () => {
        detenerAutoPlay();
        cerrarModal(modalLinea);
    });
    onEl('btn-cerrar-modal-linea-footer', 'click', () => {
        detenerAutoPlay();
        cerrarModal(modalLinea);
    });

    // Controles de navegación Línea a Línea
    onEl('btn-line-first', 'click', () => irALinea(1));
    onEl('btn-line-last', 'click', () => irALinea(AppState.totalLineasActual));
    onEl('btn-line-prev', 'click', () => irALinea(AppState.lineaActual - 1));
    onEl('btn-line-next', 'click', () => irALinea(AppState.lineaActual + 1));

    onEl('line-range-slider', 'input', (e) => {
        irALinea(parseInt(e.target.value, 10));
    });

    onEl('btn-line-autoplay', 'click', alternarAutoPlay);
    onEl('btn-cambiar-a-total', 'click', () => {
        detenerAutoPlay();
        cerrarModal(modalLinea);
        abrirModalTotal(AppState.archivoActual);
    });

    onEl('btn-copiar-linea', 'click', () => {
        const cardRaw = document.getElementById('card-raw-line');
        if (!cardRaw) return;
        navigator.clipboard.writeText(cardRaw.textContent).then(() => {
            mostrarToast('Línea copiada al portapapeles', 'success');
        });
    });

    // Cerrar al hacer clic fuera del contenedor modal
    const modalUsuario = document.getElementById('modal-usuario');
    [modalTotal, modalLinea, modalUsuario].filter(Boolean).forEach(modal => {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                detenerAutoPlay();
                cerrarModal(modal);
            }
        });
    });
}

// Carga de Datos desde la API REST (Fetch)
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
        if (data.filasOmitidas > 0 && Array.isArray(data.detallesOmitidas) && data.detallesOmitidas.length > 0) {
            const resumen = data.detallesOmitidas.slice(0, 2).join(' | ');
            const extra = data.detallesOmitidas.length > 2 ? ` (+${data.detallesOmitidas.length - 2} más)` : '';
            mostrarToast(`Filas omitidas: ${resumen}${extra}`, 'info');
        }
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

// Renderizado y Vistas
async function vaciarReservasBd() {
    if (!AppState.usuarioLogueado || !AppState.usuarioLogueado.esAdmin) {
        mostrarToast('Acceso denegado: solo los administradores pueden vaciar la base de datos.', 'error');
        return;
    }
    if (!confirm('¿Estás seguro de que quieres eliminar todas las reservas de la base de datos y los archivos generados?')) {
        return;
    }
    try {
        const res = await fetch('/api/reservas', { method: 'DELETE' });
        const data = await res.json();
        if (!res.ok) throw new Error(data.mensaje || data.error || 'Error al vaciar las reservas');

        mostrarToast(data.mensaje || 'Reservas eliminadas correctamente', 'success');
        await cargarDatosIniciales();
    } catch (err) {
        mostrarToast(err.message, 'error');
    }
}

function actualizarKPIs(data) {
    document.getElementById('kpi-total-reservas').textContent = data.totalReservas || 0;
    document.getElementById('badge-contador-reservas').textContent = data.totalReservas || 0;

    document.getElementById('kpi-total-paises').textContent = data.totalPaisesDestino || 0;

    const totalCoincidencias = data.totalGruposCoincidentes || 0;
    document.getElementById('kpi-total-coincidencias').textContent = totalCoincidencias;
    document.getElementById('badge-contador-coincidencias').textContent = totalCoincidencias;
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

// Filtros en Tiempo Real
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

async function filtrarReservasPorPais() {
    const pais = document.getElementById('select-filtro-pais').value;
    const query = document.getElementById('filtro-reservas').value.toLowerCase().trim();

    try {
        let url = '/api/reservas';
        if (pais) {
            url += `?pais=${encodeURIComponent(pais)}`;
        }
        const res = await fetch(url);
        if (res.ok) {
            const data = await res.json();
            let lista = data.reservas || [];
            if (query) {
                lista = lista.filter(r =>
                    (r.nombrePasajero && r.nombrePasajero.toLowerCase().includes(query)) ||
                    (r.origen && r.origen.toLowerCase().includes(query)) ||
                    (r.destino && r.destino.toLowerCase().includes(query)) ||
                    (r.id && r.id.toLowerCase().includes(query)) ||
                    (r.localizador && r.localizador.toLowerCase().includes(query))
                );
            }
            renderizarTablaReservas(lista);
            return;
        }
    } catch (e) {
        console.warn('Consulta directa a BD falló, usando filtro local:', e);
    }
    filtrarReservas();
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

// Modalidad Total (Full File Viewer)
async function abrirModalTotal(nombreArchivo) {
    AppState.archivoActual = nombreArchivo;
    const modal = document.getElementById('modal-total');

    try {
        const res = await fetch(`/api/reservas/archivos/${encodeURIComponent(nombreArchivo)}/total`);
        if (!res.ok) throw new Error('No se pudo consultar el archivo en modalidad total');
        const data = await res.json();

        AppState.modalidadTotalDetalle = data;

        document.getElementById('modal-total-titulo').textContent = `${data.info.nombreArchivo}`;
        document.getElementById('modal-total-metadatos').innerHTML = `
            <div class="meta-item"><span class="meta-label">Fuente:</span> <span class="meta-value" style="color:#10b981; font-weight:600;">Base de Datos H2</span></div>
            <div class="meta-item"><span class="meta-label">País ISO:</span> <span class="meta-value">${data.info.codigoPaisDestino}</span></div>
            <div class="meta-item"><span class="meta-label">Fecha:</span> <span class="meta-value">${data.info.fechaReserva}</span></div>
            <div class="meta-item"><span class="meta-label">Total Reservas en BD:</span> <span class="meta-value">${data.info.totalReservas}</span></div>
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

// Modalidad Línea a Línea (Line-by-Line Viewer)
async function abrirModalLinea(nombreArchivo, numeroLinea = 1) {
    AppState.archivoActual = nombreArchivo;
    AppState.lineaActual = numeroLinea;
    const modal = document.getElementById('modal-linea');

    document.getElementById('modal-linea-titulo').textContent = `Explorador Línea a Línea (Base de Datos H2): ${nombreArchivo}`;
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

// Utilidades de Interfaz (Tabs, Modales, Toasts)
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
    const duration = tipo === 'error' ? 6000 : 3500;
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transition = 'opacity 0.3s ease';
        setTimeout(() => toast.remove(), 300);
    }, duration);
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Módulo de Autenticación y Control de Sesión

function rellenarCredencialesPrueba(username, password) {
    const userInput = document.getElementById('login-username');
    const passInput = document.getElementById('login-password');
    const errorAlert = document.getElementById('login-error-alert');
    if (errorAlert) {
        errorAlert.style.display = 'none';
        errorAlert.textContent = '';
    }
    if (userInput) userInput.value = username;
    if (passInput) {
        passInput.value = password;
        passInput.focus();
    }
}

function initAuthEventListeners() {
    const formLogin = document.getElementById('form-login');
    if (formLogin) {
        formLogin.addEventListener('submit', (e) => {
            e.preventDefault();
            ejecutarLogin();
        });
    }

    onEl('btn-chip-admin', 'click', () => rellenarCredencialesPrueba('admin', 'admin123'));
    onEl('btn-chip-normal', 'click', () => rellenarCredencialesPrueba('usuario', 'user123'));
    onEl('btn-logout', 'click', ejecutarLogout);
}

async function verificarSesionInicial() {
    try {
        const res = await fetch('/api/auth/session');
        if (res.ok) {
            const data = await res.json();
            if (data.autenticado && data.usuario) {
                AppState.usuarioLogueado = data.usuario;
                mostrarAppPrincipal();
                await cargarDatosIniciales();
                if (AppState.usuarioLogueado.esAdmin) {
                    await cargarUsuarios();
                }
                return;
            }
        }
    } catch (e) {
        console.warn('Sin sesión previa activa:', e);
    }
    mostrarPantallaLogin();
}

function mostrarPantallaLogin() {
    const modalLogin = document.getElementById('modal-login');
    const appContainer = document.getElementById('app-container');

    if (modalLogin) modalLogin.style.display = 'flex';
    if (appContainer) appContainer.style.display = 'none';
    configurarVisibilidadSegunRol();

    const errorAlert = document.getElementById('login-error-alert');
    if (errorAlert) {
        errorAlert.style.display = 'none';
        errorAlert.textContent = '';
    }

    const usernameInput = document.getElementById('login-username');
    if (usernameInput) {
        usernameInput.value = '';
        usernameInput.focus();
    }
    const passwordInput = document.getElementById('login-password');
    if (passwordInput) passwordInput.value = '';
}

function mostrarAppPrincipal() {
    const modalLogin = document.getElementById('modal-login');
    const appContainer = document.getElementById('app-container');

    if (modalLogin) modalLogin.style.display = 'none';
    if (appContainer) appContainer.style.display = 'block';

    actualizarBadgeUsuarioHeader();
    configurarVisibilidadSegunRol();
}

function actualizarBadgeUsuarioHeader() {
    const usr = AppState.usuarioLogueado;
    if (!usr) return;

    const nombreElem = document.getElementById('header-user-name');
    const roleElem = document.getElementById('header-user-role');
    const avatarElem = document.getElementById('header-user-avatar');

    if (nombreElem) nombreElem.textContent = usr.nombre || usr.username;
    if (roleElem) {
        roleElem.textContent = usr.rol;
        roleElem.className = 'user-role-pill ' + (usr.esAdmin ? 'role-admin' : 'role-normal');
    }
    if (avatarElem) {
        avatarElem.textContent = usr.esAdmin ? '👑' : '👤';
    }
}

function configurarVisibilidadSegunRol() {
    const usr = AppState.usuarioLogueado;
    const tabBtnUsuarios = document.getElementById('tab-btn-usuarios');
    const tabUsuarios = document.getElementById('tab-usuarios');
    const btnVaciarReservas = document.getElementById('btn-vaciar-reservas');

    if (usr && usr.esAdmin) {
        if (tabBtnUsuarios) tabBtnUsuarios.style.display = 'inline-flex';
        if (btnVaciarReservas) btnVaciarReservas.style.display = 'inline-flex';
    } else {
        if (tabBtnUsuarios) tabBtnUsuarios.style.display = 'none';
        if (btnVaciarReservas) btnVaciarReservas.style.display = 'none';
        if (tabUsuarios && tabUsuarios.classList.contains('active')) {
            cambiarPestana('tab-archivos');
        }
    }
}

async function ejecutarLogin() {
    const usernameInput = document.getElementById('login-username');
    const passwordInput = document.getElementById('login-password');
    const errorAlert = document.getElementById('login-error-alert');
    const btnSubmit = document.getElementById('btn-login-submit');

    const username = usernameInput ? usernameInput.value.trim() : '';
    const password = passwordInput ? passwordInput.value : '';

    if (!username || !password) {
        if (errorAlert) {
            errorAlert.textContent = 'Por favor, introduce usuario y contraseña.';
            errorAlert.style.display = 'block';
        }
        return;
    }

    try {
        if (btnSubmit) {
            btnSubmit.disabled = true;
            btnSubmit.innerHTML = `<span>Comprobando en tabla 'usuarios'...</span>`;
        }
        if (errorAlert) errorAlert.style.display = 'none';

        const res = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        const data = await res.json();

        if (!res.ok) {
            throw new Error(data.mensaje || 'Error al iniciar sesión');
        }

        AppState.usuarioLogueado = data.usuario;
        mostrarAppPrincipal();
        mostrarToast(data.mensaje || `Bienvenido/a, ${data.usuario.nombre}`, 'success');

        await cargarDatosIniciales();
        if (AppState.usuarioLogueado.esAdmin) {
            await cargarUsuarios();
        }

    } catch (err) {
        if (errorAlert) {
            errorAlert.textContent = err.message;
            errorAlert.style.display = 'block';
        }
    } finally {
        if (btnSubmit) {
            btnSubmit.disabled = false;
            btnSubmit.innerHTML = `
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                    <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/>
                    <polyline points="10 17 15 12 10 7"/>
                    <line x1="15" y1="12" x2="3" y2="12"/>
                </svg>
                Iniciar Sesión
            `;
        }
    }
}

async function ejecutarLogout() {
    try {
        await fetch('/api/auth/logout', { method: 'POST' });
    } catch (e) {
        console.warn('Error durante logout:', e);
    }
    AppState.usuarioLogueado = null;
    AppState.usuarios = [];
    mostrarToast('Sesión finalizada. Hasta pronto.', 'info');
    mostrarPantallaLogin();
}

// Módulo de Gestión de Usuarios (Exclusivo Administrador)

function initUsuarioEventListeners() {
    const btnCrear = document.getElementById('btn-abrir-crear-usuario');
    if (btnCrear) {
        btnCrear.addEventListener('click', abrirModalCrearUsuario);
    }

    const modalUsuario = document.getElementById('modal-usuario');
    const btnCerrar = document.getElementById('btn-cerrar-modal-usuario');
    const btnCancelar = document.getElementById('btn-cancelar-modal-usuario');

    if (btnCerrar) btnCerrar.addEventListener('click', () => cerrarModal(modalUsuario));
    if (btnCancelar) btnCancelar.addEventListener('click', () => cerrarModal(modalUsuario));

    const btnGuardar = document.getElementById('btn-guardar-usuario');
    if (btnGuardar) {
        btnGuardar.addEventListener('click', guardarUsuario);
    }

    const formModal = document.getElementById('form-usuario-modal');
    if (formModal) {
        formModal.addEventListener('submit', (e) => {
            e.preventDefault();
            guardarUsuario();
        });
    }

    const filtroUsr = document.getElementById('filtro-usuarios');
    if (filtroUsr) {
        filtroUsr.addEventListener('input', filtrarUsuarios);
    }
}

async function cargarUsuarios() {
    if (!AppState.usuarioLogueado || !AppState.usuarioLogueado.esAdmin) return;

    try {
        const res = await fetch('/api/usuarios');
        if (!res.ok) {
            if (res.status === 403) return;
            throw new Error('No se pudo cargar la lista de usuarios');
        }
        AppState.usuarios = await res.json();
        const badge = document.getElementById('badge-contador-usuarios');
        if (badge) badge.textContent = AppState.usuarios.length;
        renderizarTablaUsuarios(AppState.usuarios);
    } catch (err) {
        console.error('Error al cargar usuarios:', err);
    }
}

function renderizarTablaUsuarios(usuarios) {
    const tbody = document.getElementById('tbody-usuarios');
    if (!tbody) return;

    if (!usuarios || usuarios.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="6" style="text-align: center; padding: 32px; color: var(--text-secondary);">
                    No se encontraron usuarios registrados en la tabla 'usuarios'.
                </td>
            </tr>
        `;
        return;
    }

    const idSesion = AppState.usuarioLogueado ? AppState.usuarioLogueado.id : null;

    tbody.innerHTML = usuarios.map(u => {
        const esAdmin = u.rol === 'ADMIN';
        const rolBadge = esAdmin
            ? `<span class="badge-role-admin">👑 ADMIN</span>`
            : `<span class="badge-role-normal">👤 NORMAL</span>`;

        const permisosDesc = esAdmin
            ? `<strong>Acceso total:</strong> Gestionar usuarios, vaciar BD y usar toda la página`
            : `<strong>Operativo:</strong> Usar página (subida CSV, consultas y descargas)`;

        const esPropioUsuario = idSesion && idSesion === u.id;

        return `
            <tr>
                <td><span class="badge-id">${u.id}</span></td>
                <td><strong>${escapeHtml(u.username)}</strong> ${esPropioUsuario ? '<span style="font-size:0.75rem; color:#06b6d4;">(Tú)</span>' : ''}</td>
                <td>${escapeHtml(u.nombre)}</td>
                <td>${rolBadge}</td>
                <td><span style="font-size:0.83rem; color: var(--text-secondary);">${permisosDesc}</span></td>
                <td>
                    <div class="actions-cell">
                        <button class="btn-action" onclick="abrirModalEditarUsuario(${u.id})" title="Editar usuario">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                            </svg>
                            Editar
                        </button>
                        ${!esPropioUsuario ? `
                            <button class="btn-action btn-action-delete" onclick="eliminarUsuario(${u.id}, '${escapeHtml(u.username)}')" title="Eliminar usuario">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                                    <polyline points="3 6 5 6 21 6"/>
                                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                                    <line x1="10" y1="11" x2="10" y2="17"/>
                                    <line x1="14" y1="11" x2="14" y2="17"/>
                                </svg>
                                Eliminar
                            </button>
                        ` : ''}
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}

function filtrarUsuarios() {
    const q = document.getElementById('filtro-usuarios').value.trim().toLowerCase();
    if (!q) {
        renderizarTablaUsuarios(AppState.usuarios);
        return;
    }
    const filtrados = AppState.usuarios.filter(u =>
        (u.username && u.username.toLowerCase().includes(q)) ||
        (u.nombre && u.nombre.toLowerCase().includes(q)) ||
        (u.rol && u.rol.toLowerCase().includes(q))
    );
    renderizarTablaUsuarios(filtrados);
}

function abrirModalCrearUsuario() {
    const modal = document.getElementById('modal-usuario');
    document.getElementById('modal-usuario-titulo').textContent = 'Crear Nuevo Usuario';
    document.getElementById('modal-usuario-badge').textContent = 'Alta en Tabla usuarios (H2 DB)';

    document.getElementById('input-usuario-id').value = '';
    document.getElementById('input-usuario-username').value = '';
    document.getElementById('input-usuario-username').disabled = false;
    document.getElementById('input-usuario-nombre').value = '';
    document.getElementById('input-usuario-password').value = '';
    document.getElementById('input-usuario-password').required = true;
    document.getElementById('hint-usuario-password').textContent = 'Obligatoria al crear usuario nuevo.';
    document.getElementById('select-usuario-rol').value = 'NORMAL';

    const err = document.getElementById('modal-usuario-error');
    if (err) {
        err.style.display = 'none';
        err.textContent = '';
    }

    abrirModal(modal);
}

function abrirModalEditarUsuario(id) {
    const usuario = AppState.usuarios.find(u => u.id === id);
    if (!usuario) {
        mostrarToast('Usuario no encontrado', 'error');
        return;
    }

    const modal = document.getElementById('modal-usuario');
    document.getElementById('modal-usuario-titulo').textContent = `Editar Usuario: ${usuario.username}`;
    document.getElementById('modal-usuario-badge').textContent = `Modificar ID #${usuario.id}`;

    document.getElementById('input-usuario-id').value = usuario.id;
    document.getElementById('input-usuario-username').value = usuario.username;
    document.getElementById('input-usuario-nombre').value = usuario.nombre;
    document.getElementById('input-usuario-password').value = '';
    document.getElementById('input-usuario-password').required = false;
    document.getElementById('hint-usuario-password').textContent = 'Dejar en blanco para mantener la contraseña actual.';
    document.getElementById('select-usuario-rol').value = usuario.rol || 'NORMAL';

    const err = document.getElementById('modal-usuario-error');
    if (err) {
        err.style.display = 'none';
        err.textContent = '';
    }

    abrirModal(modal);
}

async function guardarUsuario() {
    const id = document.getElementById('input-usuario-id').value;
    const username = document.getElementById('input-usuario-username').value.trim();
    const nombre = document.getElementById('input-usuario-nombre').value.trim();
    const password = document.getElementById('input-usuario-password').value;
    const rol = document.getElementById('select-usuario-rol').value;

    if (!username) {
        mostrarErrorModalUsuario('El nombre de usuario es obligatorio.');
        return;
    }
    if (!nombre) {
        mostrarErrorModalUsuario('El nombre completo es obligatorio.');
        return;
    }
    if (!id && !password) {
        mostrarErrorModalUsuario('La contraseña es obligatoria para nuevos usuarios.');
        return;
    }

    const payload = {
        username,
        nombre,
        rol
    };
    if (password && password.trim() !== '') {
        payload.password = password.trim();
    }

    const esEdicion = Boolean(id);
    const url = esEdicion ? `/api/usuarios/${id}` : '/api/usuarios';
    const method = esEdicion ? 'PUT' : 'POST';

    try {
        const res = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await res.json();
        if (!res.ok) {
            throw new Error(data.mensaje || 'Error al guardar usuario en la base de datos');
        }

        cerrarModal(document.getElementById('modal-usuario'));
        mostrarToast(data.mensaje || 'Usuario guardado correctamente', 'success');

        await cargarUsuarios();

        // Si se actualizó el usuario propio del admin en sesión, actualizar header
        if (esEdicion && AppState.usuarioLogueado && AppState.usuarioLogueado.id === Number(id)) {
            AppState.usuarioLogueado.username = username;
            AppState.usuarioLogueado.nombre = nombre;
            AppState.usuarioLogueado.rol = rol;
            AppState.usuarioLogueado.esAdmin = rol === 'ADMIN';
            actualizarBadgeUsuarioHeader();
            configurarVisibilidadSegunRol();
        }

    } catch (err) {
        mostrarErrorModalUsuario(err.message);
    }
}

function mostrarErrorModalUsuario(msg) {
    const err = document.getElementById('modal-usuario-error');
    if (err) {
        err.textContent = msg;
        err.style.display = 'block';
    }
}

async function eliminarUsuario(id, username) {
    if (!confirm(`¿Estás seguro de que deseas eliminar permanentemente al usuario '${username}' de la tabla 'usuarios'?`)) {
        return;
    }

    try {
        const res = await fetch(`/api/usuarios/${id}`, { method: 'DELETE' });
        const data = await res.json();

        if (!res.ok) {
            throw new Error(data.mensaje || 'Error al eliminar el usuario');
        }

        mostrarToast(data.mensaje || `Usuario '${username}' eliminado`, 'success');
        await cargarUsuarios();
    } catch (err) {
        mostrarToast(err.message, 'error');
    }
}
