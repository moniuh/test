import { Link } from 'react-router-dom';
import { useDocumentTitle } from '../hooks/useDocumentTitle';

export function NotFoundPage() {
  useDocumentTitle('Nie znaleziono');
  return (
    <div className="empty">
      <h1>Nie ma takiej strony</h1>
      <p>
        <Link to="/">Wróć na pulpit</Link>
      </p>
    </div>
  );
}
